/**
 * auth.js : Authentication handlers for index.html (Login & Register).
 */

document.addEventListener('DOMContentLoaded', () => {
  // If already logged in, redirect directly to dashboard
  if (Storage.getToken()) {
    window.location.href = 'dashboard.html';
    return;
  }

  // Tab switching
  const tabLogin = document.getElementById('tab-login');
  const tabRegister = document.getElementById('tab-register');
  const formLogin = document.getElementById('form-login');
  const formRegister = document.getElementById('form-register');

  if (tabLogin && tabRegister) {
    tabLogin.addEventListener('click', () => {
      tabLogin.classList.add('active');
      tabRegister.classList.remove('active');
      formLogin.style.display = 'block';
      formRegister.style.display = 'none';
    });

    tabRegister.addEventListener('click', () => {
      tabRegister.classList.add('active');
      tabLogin.classList.remove('active');
      formRegister.style.display = 'block';
      formLogin.style.display = 'none';
    });
  }

  // Handle Login
  if (formLogin) {
    formLogin.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;
      const btn = document.getElementById('btn-login-submit');

      if (!email || !password) {
        UI.showToast('Please enter both email and password', 'error');
        return;
      }

      btn.disabled = true;
      btn.innerHTML = '<span class="spinner"></span> Signing in...';

      try {
        const resp = await API.auth.login({ email, password });
        Storage.setToken(resp.token);
        Storage.setEmail(resp.email || email);
        if (resp.userId) Storage.setUserId(resp.userId);
        if (resp.roles && resp.roles.length > 0) {
          Storage.setRole(resp.roles.join(','));
        } else {
          Storage.setRole('USER');
        }

        UI.showToast('Login successful! Welcome back.', 'success');
        setTimeout(() => {
          window.location.href = 'dashboard.html';
        }, 600);
      } catch (err) {
        UI.showToast(err.message || 'Login failed. Check your credentials.', 'error');
      } finally {
        btn.disabled = false;
        btn.innerHTML = 'Sign In';
      }
    });
  }

  // Handle Register
  if (formRegister) {
    formRegister.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = document.getElementById('reg-name').value.trim();
      const email = document.getElementById('reg-email').value.trim();
      const phone = document.getElementById('reg-phone').value.trim();
      const password = document.getElementById('reg-password').value;
      const btn = document.getElementById('btn-register-submit');

      if (!name || !email || !password) {
        UI.showToast('Please fill in all required fields', 'error');
        return;
      }

      if (password.length < 6) {
        UI.showToast('Password must be at least 6 characters long', 'error');
        return;
      }

      btn.disabled = true;
      btn.innerHTML = '<span class="spinner"></span> Creating account...';

      try {
        const resp = await API.auth.register({ name, email, phone: phone || null, password });
        Storage.setToken(resp.token);
        Storage.setEmail(resp.email || email);
        if (resp.userId) Storage.setUserId(resp.userId);
        if (resp.roles && resp.roles.length > 0) {
          Storage.setRole(resp.roles.join(','));
        } else {
          Storage.setRole('USER');
        }

        UI.showToast('Account created successfully! Redirecting...', 'success');
        setTimeout(() => {
          window.location.href = 'dashboard.html';
        }, 700);
      } catch (err) {
        UI.showToast(err.message || 'Registration failed.', 'error');
      } finally {
        btn.disabled = false;
        btn.innerHTML = 'Create Account';
      }
    });
  }
});
