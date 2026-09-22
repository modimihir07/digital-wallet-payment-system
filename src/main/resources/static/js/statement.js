/**
 * statement.js : Paginated double-entry ledger history, search/filter,
 * and client-side CSV export.
 */

let currentPage = 0;
const pageSize = 15;
let currentEntries = [];

document.addEventListener('DOMContentLoaded', async () => {
  if (!Storage.getToken()) {
    window.location.href = 'index.html';
    return;
  }

  UI.initNav();
  initPagination();
  initExportBtn();
  initFilters();
  await loadStatement(currentPage);
});

async function loadStatement(page = 0) {
  const tbody = document.getElementById('statement-tbody');
  if (tbody) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; padding: 2rem;"><span class="spinner"></span> Loading ledger statement...</td></tr>';
  }

  try {
    const entries = await API.wallet.getStatement(page, pageSize);
    currentEntries = entries || [];
    renderStatementRows(currentEntries);
    updatePaginationControls(entries);
  } catch (err) {
    console.error('Failed to load statement:', err);
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><i data-lucide="alert-circle"></i><p>${err.message || 'Error loading statement'}</p></td></tr>`;
      if (window.lucide) lucide.createIcons();
    }
  }
}

function renderStatementRows(entries) {
  const tbody = document.getElementById('statement-tbody');
  if (!tbody) return;

  if (!entries || entries.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" class="empty-state">
          <i data-lucide="file-text"></i>
          <p>No ledger entries found on this page.</p>
        </td>
      </tr>
    `;
    if (window.lucide) lucide.createIcons();
    return;
  }

  tbody.innerHTML = entries.map(e => {
    const isDebit = e.entryType === 'DEBIT';
    const badgeClass = isDebit ? 'badge-danger' : 'badge-success';
    const amountColor = isDebit ? 'var(--accent-rose)' : 'var(--accent-emerald)';
    const sign = isDebit ? '-' : '+';

    return `
      <tr>
        <td style="font-weight: 500;">${UI.formatDate(e.createdAt)}</td>
        <td class="table-mono">#${e.entryId}</td>
        <td class="table-mono">Txn #${e.txnId}</td>
        <td><span class="badge ${badgeClass}">${e.entryType}</span></td>
        <td class="table-mono" style="font-weight: 700; color: ${amountColor};">
          ${sign}${UI.formatCurrency(e.amount)}
        </td>
        <td class="table-mono" style="font-weight: 600;">${UI.formatCurrency(e.balanceAfter)}</td>
      </tr>
    `;
  }).join('');
}

function updatePaginationControls(entries) {
  const pageLabel = document.getElementById('pagination-current-page');
  const prevBtn = document.getElementById('btn-prev-page');
  const nextBtn = document.getElementById('btn-next-page');

  if (pageLabel) {
    pageLabel.textContent = `Page ${currentPage + 1}`;
  }

  if (prevBtn) {
    prevBtn.disabled = currentPage === 0;
  }

  if (nextBtn) {
    // If fewer rows returned than page size, we are on the last page
    nextBtn.disabled = !entries || entries.length < pageSize;
  }
}

function initPagination() {
  const prevBtn = document.getElementById('btn-prev-page');
  const nextBtn = document.getElementById('btn-next-page');

  if (prevBtn) {
    prevBtn.addEventListener('click', async () => {
      if (currentPage > 0) {
        currentPage--;
        await loadStatement(currentPage);
      }
    });
  }

  if (nextBtn) {
    nextBtn.addEventListener('click', async () => {
      currentPage++;
      await loadStatement(currentPage);
    });
  }
}

function initFilters() {
  const typeFilter = document.getElementById('filter-entry-type');
  const searchInput = document.getElementById('statement-search');

  const applyClientFilter = () => {
    const typeVal = typeFilter ? typeFilter.value : 'ALL';
    const searchVal = searchInput ? searchInput.value.trim().toLowerCase() : '';

    let filtered = currentEntries;
    if (typeVal !== 'ALL') {
      filtered = filtered.filter(e => e.entryType === typeVal);
    }
    if (searchVal) {
      filtered = filtered.filter(e =>
        String(e.txnId).includes(searchVal) ||
        String(e.entryId).includes(searchVal) ||
        String(e.amount).includes(searchVal)
      );
    }
    renderStatementRows(filtered);
  };

  if (typeFilter) typeFilter.addEventListener('change', applyClientFilter);
  if (searchInput) searchInput.addEventListener('input', applyClientFilter);
}

function initExportBtn() {
  const exportBtn = document.getElementById('btn-export-csv');
  if (!exportBtn) return;

  exportBtn.addEventListener('click', () => {
    if (!currentEntries || currentEntries.length === 0) {
      UI.showToast('No ledger records available to export', 'error');
      return;
    }

    const headers = ['Entry ID', 'Transaction ID', 'Type', 'Amount (INR)', 'Balance After (INR)', 'Timestamp'];
    const rows = currentEntries.map(e => [
      e.entryId,
      e.txnId,
      e.entryType,
      e.amount,
      e.balanceAfter,
      `"${e.createdAt}"`
    ]);

    const csvContent = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `wallet_statement_page_${currentPage + 1}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    UI.showToast('Statement exported to CSV successfully!', 'success');
  });
}
