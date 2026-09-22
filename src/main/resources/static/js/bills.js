/**
 * bills.js : Lists user bills, provides status filter chips (All/Unpaid/Paid),
 * and handles atomic bill payments with idempotency.
 */

let allBills = [];
let currentFilter = 'ALL';
let activePayingBill = null;

document.addEventListener('DOMContentLoaded', async () => {
  if (!Storage.getToken()) {
    window.location.href = 'index.html';
    return;
  }

  UI.initNav();
  initFilterChips();
  initPayModal();
  await loadBills();
});

async function loadBills() {
  const container = document.getElementById('bills-list-container');
  if (container) {
    container.innerHTML = '<div style="padding: 2rem; text-align: center;"><div class="spinner"></div><p style="margin-top: 0.5rem; color: var(--text-muted);">Loading your bills...</p></div>';
  }

  try {
    allBills = await API.bills.getMyBills();
    renderFilteredBills();
  } catch (err) {
    console.error('Failed to load bills:', err);
    if (container) {
      container.innerHTML = `<div class="empty-state"><i data-lucide="alert-circle"></i><p>${err.message || 'Error loading bills'}</p></div>`;
      if (window.lucide) lucide.createIcons();
    }
  }
}

function initFilterChips() {
  const chips = document.querySelectorAll('.filter-chip');
  chips.forEach(chip => {
    chip.addEventListener('click', () => {
      chips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      currentFilter = chip.getAttribute('data-filter') || 'ALL';
      renderFilteredBills();
    });
  });
}

function renderFilteredBills() {
  const container = document.getElementById('bills-list-container');
  if (!container) return;

  let filtered = allBills;
  if (currentFilter === 'UNPAID') {
    filtered = allBills.filter(b => b.status === 'UNPAID' || b.status === 'OVERDUE');
  } else if (currentFilter === 'PAID') {
    filtered = allBills.filter(b => b.status === 'PAID');
  }

  if (filtered.length === 0) {
    container.innerHTML = `
      <div class="empty-state">
        <i data-lucide="receipt"></i>
        <p>No ${currentFilter.toLowerCase()} bills found.</p>
      </div>
    `;
    if (window.lucide) lucide.createIcons();
    return;
  }

  container.innerHTML = `
    <div class="table-responsive">
      <table class="table">
        <thead>
          <tr>
            <th>Merchant</th>
            <th>Category</th>
            <th>Due Date</th>
            <th>Amount</th>
            <th>Status</th>
            <th style="text-align: right;">Action</th>
          </tr>
        </thead>
        <tbody>
          ${filtered.map(b => {
            const isUnpaid = b.status === 'UNPAID' || b.status === 'OVERDUE';
            const merchantDisplay = b.merchantName || `Merchant #${b.merchantId}`;
            const categoryIcon = getCategoryIcon(b.merchantName);

            return `
              <tr>
                <td>
                  <div style="display: flex; align-items: center; gap: 0.75rem;">
                    <div style="width: 36px; height: 36px; border-radius: var(--radius-md); background: var(--bg-tertiary); display: flex; align-items: center; justify-content: center; color: var(--primary);">
                      <i data-lucide="${categoryIcon}"></i>
                    </div>
                    <div>
                      <div style="font-weight: 700;">${merchantDisplay}</div>
                      <div style="font-size: 0.75rem; color: var(--text-muted);">Bill #${b.billId}</div>
                    </div>
                  </div>
                </td>
                <td><span class="badge badge-info">${getCategoryName(b.merchantName)}</span></td>
                <td style="color: var(--text-secondary);">${b.dueDate || 'N/A'}</td>
                <td class="table-mono" style="font-weight: 700; font-size: 1rem;">${UI.formatCurrency(b.amount)}</td>
                <td><span class="badge badge-${b.status}">${b.status}</span></td>
                <td style="text-align: right;">
                  ${isUnpaid ? `
                    <button class="btn btn-primary btn-sm btn-pay-bill" data-bill-id="${b.billId}" data-merchant="${merchantDisplay}" data-amount="${b.amount}">
                      <i data-lucide="credit-card"></i> Pay Now
                    </button>
                  ` : `
                    <span style="font-size: 0.8rem; color: var(--accent-emerald); display: inline-flex; align-items: center; gap: 0.25rem;">
                      <i data-lucide="check"></i> Paid
                    </span>
                  `}
                </td>
              </tr>
            `;
          }).join('')}
        </tbody>
      </table>
    </div>
  `;

  if (window.lucide) lucide.createIcons();

  // Attach pay button listeners
  const payButtons = container.querySelectorAll('.btn-pay-bill');
  payButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      activePayingBill = {
        billId: parseInt(btn.getAttribute('data-bill-id')),
        merchant: btn.getAttribute('data-merchant'),
        amount: parseFloat(btn.getAttribute('data-amount'))
      };

      const modalMerchant = document.getElementById('modal-pay-merchant');
      const modalAmount = document.getElementById('modal-pay-amount');

      if (modalMerchant) modalMerchant.textContent = activePayingBill.merchant;
      if (modalAmount) modalAmount.textContent = UI.formatCurrency(activePayingBill.amount);

      UI.openModal('modal-confirm-pay');
    });
  });
}

function initPayModal() {
  const confirmBtn = document.getElementById('btn-confirm-pay-submit');
  const cancelBtn = document.getElementById('btn-confirm-pay-cancel');

  if (cancelBtn) {
    cancelBtn.addEventListener('click', () => {
      UI.closeModal('modal-confirm-pay');
      activePayingBill = null;
    });
  }

  if (confirmBtn) {
    confirmBtn.addEventListener('click', async () => {
      if (!activePayingBill) return;

      confirmBtn.disabled = true;
      confirmBtn.innerHTML = 'Processing Payment...';

      try {
        const resp = await API.bills.pay(activePayingBill.billId);
        UI.showToast(`Bill #${activePayingBill.billId} paid successfully! Txn #${resp.txnId}`, 'success');
        UI.closeModal('modal-confirm-pay');
        activePayingBill = null;
        await loadBills();
      } catch (err) {
        UI.showToast(err.message || 'Payment failed. Ensure your wallet has sufficient balance.', 'error');
      } finally {
        confirmBtn.disabled = false;
        confirmBtn.innerHTML = 'Confirm & Pay';
      }
    });
  }
}

function getCategoryIcon(merchantName) {
  const name = (merchantName || '').toLowerCase();
  if (name.includes('power') || name.includes('electricity')) return 'zap';
  if (name.includes('airtel') || name.includes('mobile') || name.includes('recharge')) return 'smartphone';
  if (name.includes('netflix') || name.includes('entertainment')) return 'tv';
  if (name.includes('zomato') || name.includes('swiggy') || name.includes('food')) return 'utensils';
  if (name.includes('uber') || name.includes('transport') || name.includes('irctc')) return 'car';
  return 'shopping-bag';
}

function getCategoryName(merchantName) {
  const name = (merchantName || '').toLowerCase();
  if (name.includes('power')) return 'Electricity';
  if (name.includes('airtel')) return 'Mobile';
  if (name.includes('netflix')) return 'Entertainment';
  if (name.includes('zomato') || name.includes('swiggy')) return 'Food & Dining';
  if (name.includes('uber') || name.includes('irctc')) return 'Travel';
  if (name.includes('amazon') || name.includes('flipkart')) return 'Shopping';
  return 'Utility';
}
