/**
 * transfer.js : Handles P2P transfers with client-generated idempotency key,
 * balance pre-checks, and success confirmation modal.
 */

let currentSenderWallet = null;

document.addEventListener('DOMContentLoaded', async () => {
  if (!Storage.getToken()) {
    window.location.href = 'index.html';
    return;
  }

  UI.initNav();
  await loadSenderWallet();
  initTransferForm();
});

async function loadSenderWallet() {
  try {
    currentSenderWallet = await API.wallet.getMe();
    const balanceEl = document.getElementById('sender-balance-display');
    const senderIdEl = document.getElementById('sender-wallet-id');
    const warningEl = document.getElementById('wallet-frozen-warning');

    if (balanceEl) {
      balanceEl.textContent = UI.formatCurrency(currentSenderWallet.balance);
    }
    if (senderIdEl) {
      senderIdEl.textContent = `Wallet #${currentSenderWallet.walletId}`;
    }

    if (currentSenderWallet.status === 'FROZEN') {
      if (warningEl) warningEl.style.display = 'block';
      const submitBtn = document.getElementById('btn-submit-transfer');
      if (submitBtn) submitBtn.disabled = true;
    }
  } catch (err) {
    console.error('Failed to load wallet:', err);
    UI.showToast('Could not load sender wallet details', 'error');
  }
}

function initTransferForm() {
  const form = document.getElementById('form-transfer');
  const amountInput = document.getElementById('transfer-amount');
  const recipientInput = document.getElementById('transfer-recipient');
  const remarksInput = document.getElementById('transfer-remarks');
  const chipBtns = document.querySelectorAll('.transfer-chip');
  const submitBtn = document.getElementById('btn-submit-transfer');

  // Amount quick chips
  chipBtns.forEach(chip => {
    chip.addEventListener('click', () => {
      const val = chip.getAttribute('data-amount');
      if (amountInput) amountInput.value = val;
    });
  });

  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      if (!currentSenderWallet) {
        UI.showToast('Sender wallet not loaded. Please refresh.', 'error');
        return;
      }

      const toWalletId = parseInt(recipientInput.value.trim());
      const amount = parseFloat(amountInput.value);
      const remarks = remarksInput.value.trim();

      if (!toWalletId || isNaN(toWalletId)) {
        UI.showToast('Please enter a valid recipient Wallet ID (e.g. 2)', 'error');
        return;
      }

      if (toWalletId === currentSenderWallet.walletId) {
        UI.showToast('Cannot transfer money to your own wallet ID', 'error');
        return;
      }

      if (!amount || amount <= 0) {
        UI.showToast('Please enter a transfer amount greater than ₹0', 'error');
        return;
      }

      if (amount > parseFloat(currentSenderWallet.balance)) {
        UI.showToast('Insufficient wallet balance to complete this transfer', 'error');
        return;
      }

      // Generate client-side idempotency key
      const idempotencyKey = crypto.randomUUID();

      submitBtn.disabled = true;
      submitBtn.innerHTML = '<span class="spinner"></span> Processing Transfer...';

      try {
        const resp = await API.transfer.send({
          fromWalletId: currentSenderWallet.walletId,
          toWalletId: toWalletId,
          amount: amount,
          remarks: remarks
        }, idempotencyKey);

        // Populate and open success modal
        const modalTxnId = document.getElementById('modal-success-txnid');
        const modalAmount = document.getElementById('modal-success-amount');
        const modalRecipient = document.getElementById('modal-success-recipient');
        const modalReplayed = document.getElementById('modal-success-replayed');

        if (modalTxnId) modalTxnId.textContent = `#${resp.txnId}`;
        if (modalAmount) modalAmount.textContent = UI.formatCurrency(resp.amount || amount);
        if (modalRecipient) modalRecipient.textContent = `Wallet #${toWalletId}`;
        if (modalReplayed) {
          modalReplayed.style.display = resp.replayed ? 'inline-block' : 'none';
        }

        UI.openModal('modal-transfer-success');

        // Reload sender wallet balance
        await loadSenderWallet();

        // Reset form inputs
        form.reset();

      } catch (err) {
        UI.showToast(err.message || 'Transfer failed. Please verify recipient wallet ID.', 'error');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<i data-lucide="send"></i> Send Money Now';
        if (window.lucide) lucide.createIcons();
      }
    });
  }

  // Modal actions
  const btnSendAgain = document.getElementById('btn-send-again');
  if (btnSendAgain) {
    btnSendAgain.addEventListener('click', () => {
      UI.closeModal('modal-transfer-success');
    });
  }

  const btnViewStatement = document.getElementById('btn-view-statement');
  if (btnViewStatement) {
    btnViewStatement.addEventListener('click', () => {
      window.location.href = 'statement.html';
    });
  }
}
