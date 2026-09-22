/**
 * dashboard.js : Handles wallet balance, quick actions, recent activity,
 * Chart.js analytics, and Top-Up modal.
 */

let activityChart = null;

document.addEventListener('DOMContentLoaded', async () => {
  if (!Storage.getToken()) {
    window.location.href = 'index.html';
    return;
  }

  UI.initNav();
  initTopUpModal();
  await loadDashboardData();
});

async function loadDashboardData() {
  try {
    // 1. Fetch wallet details
    const wallet = await API.wallet.getMe();
    sessionStorage.setItem('current_wallet_id', wallet.walletId);

    const balanceEl = document.getElementById('wallet-balance');
    const statusPill = document.getElementById('wallet-status-pill');
    const walletIdEl = document.getElementById('wallet-id-badge');

    if (balanceEl) {
      UI.animateCount(balanceEl, wallet.balance);
    }
    if (statusPill) {
      statusPill.textContent = wallet.status;
      statusPill.className = `badge badge-${wallet.status}`;
    }
    if (walletIdEl) {
      walletIdEl.textContent = `Wallet #${wallet.walletId}`;
    }

    // 2. Fetch statement for recent transactions and analytics
    const statement = await API.wallet.getStatement(0, 20);
    renderRecentTransactions(statement);
    calculateQuickStats(statement);
    renderSpendingChart(statement);

    // 3. Fetch bills for unpaid count
    try {
      const bills = await API.bills.getMyBills();
      const unpaid = bills.filter(b => b.status === 'UNPAID');
      const unpaidCountEl = document.getElementById('unpaid-bills-count');
      if (unpaidCountEl) {
        unpaidCountEl.textContent = unpaid.length;
      }
    } catch (_) {
      // bills fetch fail-safe
    }

  } catch (err) {
    console.error('Failed to load dashboard:', err);
    UI.showToast(err.message || 'Error loading dashboard data', 'error');
  }
}

function renderRecentTransactions(entries) {
  const tbody = document.getElementById('recent-transactions-tbody');
  if (!tbody) return;

  if (!entries || entries.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="empty-state">
          <i data-lucide="inbox"></i>
          <p>No transactions found. Start by sending or topping up money!</p>
        </td>
      </tr>
    `;
    if (window.lucide) lucide.createIcons();
    return;
  }

  tbody.innerHTML = entries.slice(0, 8).map(entry => {
    const isDebit = entry.entryType === 'DEBIT';
    const amountClass = isDebit ? 'badge-danger' : 'badge-success';
    const sign = isDebit ? '-' : '+';
    const dateFormatted = UI.formatDate(entry.createdAt);

    return `
      <tr>
        <td style="font-weight: 500;">${dateFormatted}</td>
        <td><span class="badge ${amountClass}">${entry.entryType}</span></td>
        <td class="table-mono" style="font-weight: 700; color: ${isDebit ? 'var(--accent-rose)' : 'var(--accent-emerald)'};">
          ${sign}${UI.formatCurrency(entry.amount)}
        </td>
        <td class="table-mono">${UI.formatCurrency(entry.balanceAfter)}</td>
        <td><span class="badge badge-success">COMPLETED</span></td>
      </tr>
    `;
  }).join('');
}

function calculateQuickStats(entries) {
  let sentTotal = 0;
  let receivedTotal = 0;

  if (entries && Array.isArray(entries)) {
    entries.forEach(e => {
      const amt = parseFloat(e.amount) || 0;
      if (e.entryType === 'DEBIT') sentTotal += amt;
      if (e.entryType === 'CREDIT') receivedTotal += amt;
    });
  }

  const sentEl = document.getElementById('stat-total-sent');
  const receivedEl = document.getElementById('stat-total-received');

  if (sentEl) sentEl.textContent = UI.formatCurrency(sentTotal);
  if (receivedEl) receivedEl.textContent = UI.formatCurrency(receivedTotal);
}

function renderSpendingChart(entries) {
  const chartCanvas = document.getElementById('activity-chart');
  if (!chartCanvas || !window.Chart) return;

  // Build daily debits for the last 7 days
  const daysMap = {};
  const today = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const key = d.toISOString().split('T')[0];
    const label = d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric' });
    daysMap[key] = { label, amount: 0 };
  }

  if (entries && Array.isArray(entries)) {
    entries.forEach(e => {
      if (e.entryType === 'DEBIT' && e.createdAt) {
        const dateKey = e.createdAt.split('T')[0];
        if (daysMap[dateKey]) {
          daysMap[dateKey].amount += parseFloat(e.amount) || 0;
        }
      }
    });
  }

  const labels = Object.values(daysMap).map(v => v.label);
  const data = Object.values(daysMap).map(v => v.amount);

  if (activityChart) {
    activityChart.destroy();
  }

  const ctx = chartCanvas.getContext('2d');
  const gradient = ctx.createLinearGradient(0, 0, 0, 240);
  gradient.addColorStop(0, 'rgba(99, 102, 241, 0.45)');
  gradient.addColorStop(1, 'rgba(99, 102, 241, 0.0)');

  activityChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Spending (₹)',
        data,
        borderColor: '#6366F1',
        borderWidth: 2.5,
        backgroundColor: gradient,
        fill: true,
        tension: 0.35,
        pointBackgroundColor: '#6366F1',
        pointBorderColor: '#FFFFFF',
        pointBorderWidth: 1.5,
        pointRadius: 4,
        pointHoverRadius: 6
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#0F172A',
          borderColor: 'rgba(255, 255, 255, 0.1)',
          borderWidth: 1,
          padding: 10,
          displayColors: false,
          callbacks: {
            label: (ctx) => `Spent: ₹${ctx.parsed.y.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { color: '#94A3B8', font: { size: 11 } }
        },
        y: {
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: {
            color: '#94A3B8',
            font: { size: 11 },
            callback: (v) => '₹' + v
          }
        }
      }
    }
  });
}

function initTopUpModal() {
  const topUpModal = document.getElementById('modal-topup');
  const openBtns = document.querySelectorAll('.open-topup-btn');
  const closeBtns = document.querySelectorAll('.close-modal-btn');
  const topUpForm = document.getElementById('form-topup');
  const amountInput = document.getElementById('topup-amount');
  const chipBtns = document.querySelectorAll('.topup-chip');

  openBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      if (amountInput) amountInput.value = '';
      UI.openModal('modal-topup');
    });
  });

  closeBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      UI.closeModal('modal-topup');
    });
  });

  chipBtns.forEach(chip => {
    chip.addEventListener('click', () => {
      const val = chip.getAttribute('data-amount');
      if (amountInput) amountInput.value = val;
    });
  });

  if (topUpForm) {
    topUpForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const amount = parseFloat(amountInput.value);
      if (!amount || amount <= 0) {
        UI.showToast('Please enter a valid amount greater than ₹0', 'error');
        return;
      }

      const submitBtn = document.getElementById('btn-submit-topup');
      submitBtn.disabled = true;
      submitBtn.innerHTML = 'Processing...';

      try {
        const resp = await API.wallet.topUp(amount);
        UI.showToast(`Successfully added ${UI.formatCurrency(amount)} to your wallet!`, 'success');
        UI.closeModal('modal-topup');
        await loadDashboardData();
      } catch (err) {
        UI.showToast(err.message || 'Top-up failed', 'error');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Confirm Top-Up';
      }
    });
  }
}
