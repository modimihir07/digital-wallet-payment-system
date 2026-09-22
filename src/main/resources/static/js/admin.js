/**
 * admin.js : Admin portal for wallet freeze/unfreeze, trigger audit logs,
 * daily reporting view charts, and top spenders leaderboard.
 */

let dailyChart = null;

document.addEventListener('DOMContentLoaded', async () => {
  if (!Storage.getToken()) {
    window.location.href = 'index.html';
    return;
  }

  // Admin role gate
  if (!Storage.isAdmin()) {
    UI.showToast('Access Denied: Administrator role required', 'error');
    setTimeout(() => {
      window.location.href = 'dashboard.html';
    }, 1000);
    return;
  }

  UI.initNav();
  initDateFilter();
  await loadAdminData();
});

async function loadAdminData() {
  await Promise.allSettled([
    loadWallets(),
    loadAuditLogs(),
    loadDailyReport(),
    loadTopUsers()
  ]);
}

// 1. Wallets Management
async function loadWallets() {
  const tbody = document.getElementById('admin-wallets-tbody');
  if (tbody) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; padding: 1.5rem;"><span class="spinner"></span> Loading wallets...</td></tr>';
  }

  try {
    const wallets = await API.admin.getWallets(0, 50);
    if (!wallets || wallets.length === 0) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="empty-state"><p>No wallets found.</p></td></tr>';
      return;
    }

    if (tbody) {
      tbody.innerHTML = wallets.map(w => {
        const isFrozen = w.status === 'FROZEN';
        return `
          <tr>
            <td class="table-mono" style="font-weight: 700;">#${w.walletId}</td>
            <td class="table-mono">User #${w.userId}</td>
            <td class="table-mono" style="font-weight: 700;">${UI.formatCurrency(w.balance)}</td>
            <td><span class="badge badge-${w.status}">${w.status}</span></td>
            <td>${UI.formatDate(w.createdAt)}</td>
            <td style="text-align: right;">
              ${isFrozen ? `
                <button class="btn btn-primary btn-sm btn-wallet-action" data-action="unfreeze" data-id="${w.walletId}">
                  <i data-lucide="unlock"></i> Unfreeze
                </button>
              ` : `
                <button class="btn btn-danger btn-sm btn-wallet-action" data-action="freeze" data-id="${w.walletId}">
                  <i data-lucide="lock"></i> Freeze
                </button>
              `}
            </td>
          </tr>
        `;
      }).join('');

      if (window.lucide) lucide.createIcons();

      // Attach action listeners
      const actionBtns = tbody.querySelectorAll('.btn-wallet-action');
      actionBtns.forEach(btn => {
        btn.addEventListener('click', async () => {
          const action = btn.getAttribute('data-action');
          const walletId = btn.getAttribute('data-id');
          btn.disabled = true;

          try {
            if (action === 'freeze') {
              await API.admin.freezeWallet(walletId);
              UI.showToast(`Wallet #${walletId} frozen successfully`, 'warning');
            } else {
              await API.admin.unfreezeWallet(walletId);
              UI.showToast(`Wallet #${walletId} unfrozen to ACTIVE`, 'success');
            }
            await loadWallets();
            await loadAuditLogs(); // Trigger would have logged this!
          } catch (err) {
            UI.showToast(err.message || 'Operation failed', 'error');
            btn.disabled = false;
          }
        });
      });
    }
  } catch (err) {
    console.error('Failed to load wallets:', err);
    if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

// 2. Audit Logs (Trigger generated)
async function loadAuditLogs() {
  const tbody = document.getElementById('admin-audit-tbody');
  if (tbody) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; padding: 1.5rem;"><span class="spinner"></span> Loading audit trail...</td></tr>';
  }

  try {
    const logs = await API.admin.getAudit(0, 30);
    if (!logs || logs.length === 0) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="empty-state"><p>No audit records found.</p></td></tr>';
      return;
    }

    if (tbody) {
      tbody.innerHTML = logs.map(l => {
        let oldDisplay = l.oldValue || '-';
        let newDisplay = l.newValue || '-';

        return `
          <tr>
            <td class="table-mono" style="font-weight: 600;">#${l.logId}</td>
            <td style="font-weight: 600;">${UI.formatDate(l.changedAt)}</td>
            <td><span class="badge badge-info">${l.tableName}</span></td>
            <td><span class="badge badge-warning">${l.action}</span></td>
            <td class="table-mono">#${l.recordId || '-'}</td>
            <td style="font-family: var(--font-mono); font-size: 0.75rem; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="Old: ${oldDisplay} | New: ${newDisplay}">
              <span style="color: var(--accent-rose);">${oldDisplay}</span>
              &rarr;
              <span style="color: var(--accent-emerald);">${newDisplay}</span>
            </td>
          </tr>
        `;
      }).join('');
    }
  } catch (err) {
    console.error('Failed to load audit logs:', err);
    if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

// 3. Daily Summary Report (View generated)
async function loadDailyReport(date = null) {
  const targetDate = date || new Date().toISOString().split('T')[0];
  const dateInput = document.getElementById('report-date-picker');
  if (dateInput && !date) dateInput.value = targetDate;

  try {
    const report = await API.admin.getDailyReport(targetDate);

    const volumeEl = document.getElementById('daily-volume');
    const txnsEl = document.getElementById('daily-txns');
    const successEl = document.getElementById('daily-success');
    const failEl = document.getElementById('daily-fail');

    if (volumeEl) volumeEl.textContent = UI.formatCurrency(report.totalVolume || 0);
    if (txnsEl) txnsEl.textContent = report.totalTxns || 0;
    if (successEl) successEl.textContent = report.successCount || 0;
    if (failEl) failEl.textContent = report.failCount || 0;

    renderDailyChart(report);
  } catch (err) {
    console.error('Failed to load daily report:', err);
  }
}

function renderDailyChart(report) {
  const canvas = document.getElementById('daily-summary-chart');
  if (!canvas || !window.Chart) return;

  const success = report.successCount || 0;
  const fail = report.failCount || 0;

  if (dailyChart) dailyChart.destroy();

  const ctx = canvas.getContext('2d');
  dailyChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: ['Successful', 'Failed / Reversed'],
      datasets: [{
        data: [success, fail],
        backgroundColor: ['#10B981', '#F43F5E'],
        borderWidth: 0
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'bottom', labels: { color: '#94A3B8' } }
      }
    }
  });
}

// 4. Top Spenders (View generated)
async function loadTopUsers() {
  const tbody = document.getElementById('top-users-tbody');
  if (tbody) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 1.5rem;"><span class="spinner"></span> Loading leaderboard...</td></tr>';
  }

  try {
    const users = await API.admin.getTopUsers();
    if (!users || users.length === 0) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="5" class="empty-state"><p>No user data available.</p></td></tr>';
      return;
    }

    if (tbody) {
      tbody.innerHTML = users.map((u, idx) => {
        let rankBadge = `<span class="table-mono" style="font-weight: 700;">#${idx + 1}</span>`;
        if (idx === 0) rankBadge = '🥇';
        if (idx === 1) rankBadge = '🥈';
        if (idx === 2) rankBadge = '🥉';

        return `
          <tr>
            <td style="font-size: 1.1rem; width: 50px;">${rankBadge}</td>
            <td style="font-weight: 700;">${u.userName || 'User #' + u.userId}</td>
            <td style="color: var(--text-secondary);">${u.email || '-'}</td>
            <td class="table-mono" style="font-weight: 700; color: var(--accent-rose);">${UI.formatCurrency(u.totalOutgoing)}</td>
            <td class="table-mono" style="font-weight: 600;">${u.txnCount} txns</td>
          </tr>
        `;
      }).join('');
    }
  } catch (err) {
    console.error('Failed to load top users:', err);
    if (tbody) tbody.innerHTML = `<tr><td colspan="5" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

function initDateFilter() {
  const datePicker = document.getElementById('report-date-picker');
  if (datePicker) {
    datePicker.addEventListener('change', () => {
      loadDailyReport(datePicker.value);
    });
  }
}
