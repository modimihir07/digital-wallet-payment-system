/**
 * api.js : Central API client with automatic JWT bearer attachment,
 * error parsing, and session management.
 */

const API_BASE = '/api';

const Storage = {
  getToken: () => localStorage.getItem('wallet_token'),
  setToken: (t) => localStorage.setItem('wallet_token', t),
  getRole: () => localStorage.getItem('wallet_role'),
  setRole: (r) => localStorage.setItem('wallet_role', r),
  getEmail: () => localStorage.getItem('wallet_email'),
  setEmail: (e) => localStorage.setItem('wallet_email', e),
  getUserId: () => localStorage.getItem('wallet_user_id'),
  setUserId: (id) => localStorage.setItem('wallet_user_id', id),
  clear: () => {
    localStorage.removeItem('wallet_token');
    localStorage.removeItem('wallet_role');
    localStorage.removeItem('wallet_email');
    localStorage.removeItem('wallet_user_id');
  },
  isAdmin: () => {
    const role = localStorage.getItem('wallet_role');
    return role && (role.includes('ADMIN') || role === 'ROLE_ADMIN');
  }
};

/**
 * Universal request wrapper.
 */
async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  const token = Storage.getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers
    });

    if (response.status === 401) {
      Storage.clear();
      if (!window.location.pathname.endsWith('index.html') && window.location.pathname !== '/') {
        window.location.href = 'index.html';
      }
      throw new Error('Session expired or unauthorized. Please log in again.');
    }

    if (!response.ok) {
      let errorMsg = `Error ${response.status}: ${response.statusText}`;
      try {
        const errorJson = await response.json();
        errorMsg = errorJson.message || errorJson.error || JSON.stringify(errorJson);
      } catch (_) {
        const errorText = await response.text();
        if (errorText) errorMsg = errorText;
      }
      throw new Error(errorMsg);
    }

    // Handle empty responses
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      return await response.json();
    }
    return await response.text();
  } catch (err) {
    console.error(`API Error [${endpoint}]:`, err);
    throw err;
  }
}

const API = {
  // Auth
  auth: {
    register: (data) => apiRequest('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
    login: (data) => apiRequest('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
    logout: () => {
      Storage.clear();
      window.location.href = 'index.html';
    }
  },

  // Wallet
  wallet: {
    getMe: () => apiRequest('/wallet/me', { method: 'GET' }),
    getStatement: (page = 0, size = 20) => apiRequest(`/wallet/me/statement?page=${page}&size=${size}`, { method: 'GET' }),
    topUp: (amount, idempotencyKey = null) => {
      const key = idempotencyKey || crypto.randomUUID();
      return apiRequest('/wallet/topup', {
        method: 'POST',
        headers: { 'X-Idempotency-Key': key },
        body: JSON.stringify({ amount: parseFloat(amount), idempotencyKey: key })
      });
    }
  },

  // Transfers
  transfer: {
    send: (payload, idempotencyKey = null) => {
      const key = idempotencyKey || crypto.randomUUID();
      return apiRequest('/transfer', {
        method: 'POST',
        headers: { 'X-Idempotency-Key': key },
        body: JSON.stringify({
          fromWalletId: payload.fromWalletId,
          toWalletId: payload.toWalletId,
          amount: parseFloat(payload.amount),
          remarks: payload.remarks || '',
          idempotencyKey: key
        })
      });
    },
    get: (txnId) => apiRequest(`/transfer/${txnId}`, { method: 'GET' })
  },

  // Bills
  bills: {
    getMyBills: () => apiRequest('/bills/my', { method: 'GET' }),
    pay: (billId, idempotencyKey = null) => {
      const key = idempotencyKey || crypto.randomUUID();
      return apiRequest(`/bills/${billId}/pay`, {
        method: 'POST',
        headers: { 'X-Idempotency-Key': key }
      });
    }
  },

  // Admin
  admin: {
    getWallets: (page = 0, size = 50) => apiRequest(`/admin/wallets?page=${page}&size=${size}`, { method: 'GET' }),
    freezeWallet: (id) => apiRequest(`/admin/wallets/${id}/freeze`, { method: 'POST' }),
    unfreezeWallet: (id) => apiRequest(`/admin/wallets/${id}/unfreeze`, { method: 'POST' }),
    getAudit: (page = 0, size = 50) => apiRequest(`/admin/audit?page=${page}&size=${size}`, { method: 'GET' }),
    getDailyReport: (date) => apiRequest(`/admin/reports/daily?date=${date}`, { method: 'GET' }),
    getTopUsers: () => apiRequest('/admin/reports/top-users', { method: 'GET' })
  }
};
