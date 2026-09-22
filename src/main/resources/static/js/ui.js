/**
 * ui.js : UI helpers, Theme manager, Toast notification system,
 * Modal controller, and number animations.
 */

const UI = {
  // Theme Management
  initTheme: () => {
    const savedTheme = localStorage.getItem('wallet_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', savedTheme);
    UI.updateThemeToggleIcon(savedTheme);
  },

  toggleTheme: () => {
    const current = document.documentElement.getAttribute('data-theme') || 'dark';
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('wallet_theme', next);
    UI.updateThemeToggleIcon(next);
  },

  updateThemeToggleIcon: (theme) => {
    const icons = document.querySelectorAll('.theme-toggle-icon');
    icons.forEach(icon => {
      if (theme === 'light') {
        icon.innerHTML = '<i data-lucide="moon"></i>';
      } else {
        icon.innerHTML = '<i data-lucide="sun"></i>';
      }
    });
    if (window.lucide) lucide.createIcons();
  },

  // Toast System
  showToast: (message, type = 'info', duration = 3500) => {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    let iconName = 'info';
    if (type === 'success') iconName = 'check-circle-2';
    if (type === 'error') iconName = 'alert-triangle';

    toast.innerHTML = `
      <i data-lucide="${iconName}" style="color: ${type === 'success' ? 'var(--accent-emerald)' : type === 'error' ? 'var(--accent-rose)' : 'var(--accent-cyan)'}; flex-shrink: 0; margin-top: 2px;"></i>
      <div class="toast-message">${message}</div>
      <button class="toast-close">&times;</button>
    `;

    container.appendChild(toast);
    if (window.lucide) lucide.createIcons();

    const closeBtn = toast.querySelector('.toast-close');
    const dismiss = () => {
      toast.style.animation = 'none';
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(20px)';
      toast.style.transition = 'all 0.25s ease';
      setTimeout(() => toast.remove(), 250);
    };

    closeBtn.addEventListener('click', dismiss);
    const timer = setTimeout(dismiss, duration);

    toast.addEventListener('mouseenter', () => clearTimeout(timer));
  },

  // Modal Management
  openModal: (modalId) => {
    const modal = document.getElementById(modalId);
    if (modal) {
      modal.classList.add('open');
      document.body.style.overflow = 'hidden';
    }
  },

  closeModal: (modalId) => {
    const modal = document.getElementById(modalId);
    if (modal) {
      modal.classList.remove('open');
      document.body.style.overflow = '';
    }
  },

  // Animated Number Count-Up
  animateCount: (element, targetValue, duration = 1000) => {
    if (!element) return;
    const start = 0;
    const target = parseFloat(targetValue) || 0;
    const startTime = performance.now();

    const update = (currentTime) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // Ease out cubic
      const easeProgress = 1 - Math.pow(1 - progress, 3);
      const current = start + (target - start) * easeProgress;
      element.textContent = current.toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      });

      if (progress < 1) {
        requestAnimationFrame(update);
      } else {
        element.textContent = target.toLocaleString('en-IN', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2
        });
      }
    };

    requestAnimationFrame(update);
  },

  // Formatting Helpers
  formatCurrency: (amount) => {
    const val = parseFloat(amount) || 0;
    return '₹' + val.toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  },

  formatDate: (isoString) => {
    if (!isoString) return '-';
    const d = new Date(isoString);
    return d.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  },

  // Navigation and Header setup
  initNav: () => {
    const email = Storage.getEmail();
    const isAdmin = Storage.isAdmin();

    // Set user email pill
    const userPills = document.querySelectorAll('.nav-user-email');
    userPills.forEach(el => {
      if (email) el.textContent = email;
    });

    const userAvatars = document.querySelectorAll('.user-avatar');
    userAvatars.forEach(el => {
      if (email) el.textContent = email.charAt(0).toUpperCase();
    });

    // Show or hide admin link
    const adminLinks = document.querySelectorAll('.admin-only-link');
    adminLinks.forEach(el => {
      el.style.display = isAdmin ? 'flex' : 'none';
    });

    // Theme toggle buttons
    const themeButtons = document.querySelectorAll('.theme-toggle-btn');
    themeButtons.forEach(btn => {
      btn.addEventListener('click', UI.toggleTheme);
    });

    // Logout buttons
    const logoutBtns = document.querySelectorAll('.logout-btn');
    logoutBtns.forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        API.auth.logout();
      });
    });

    // Mobile nav toggle
    const mobileToggle = document.querySelector('.mobile-nav-toggle');
    const navLinks = document.querySelector('.nav-links');
    if (mobileToggle && navLinks) {
      mobileToggle.addEventListener('click', () => {
        navLinks.classList.toggle('open');
      });
    }

    if (window.lucide) lucide.createIcons();
  }
};

// Auto-run theme on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  UI.initTheme();
  if (window.lucide) lucide.createIcons();
});
