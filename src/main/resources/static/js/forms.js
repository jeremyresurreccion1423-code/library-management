(() => {
    const EMAIL_RE = /^[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}$/;

    function isAuthPage() {
        return Boolean(document.querySelector(".login-split, .register-card, [data-auth-page]"));
    }

    function cleanupModalBackdrops() {
        document.querySelectorAll(".modal-backdrop").forEach((el) => el.remove());
        document.body.classList.remove("modal-open");
        document.body.style.overflow = "";
        document.body.style.paddingRight = "";
    }

    function confirmAction(message, optionsOrCallback, callback) {
        let options = {};
        let onConfirm = null;
        if (typeof optionsOrCallback === "function") {
            onConfirm = optionsOrCallback;
        } else if (typeof optionsOrCallback === "object" && optionsOrCallback !== null) {
            options = optionsOrCallback;
            onConfirm = callback;
        }

        const modalElement = document.getElementById("confirmModal");
        if (!modalElement || typeof bootstrap === "undefined" || !bootstrap.Modal) {
            if (confirm(message) && typeof onConfirm === "function") onConfirm();
            return Promise.resolve(false);
        }

        const title = options.title || (options.type === "danger" ? "Confirm Delete" : "Confirm Action");
        const okText = options.okText || (options.type === "danger" ? "Yes, Delete" : "Confirm");
        const type = options.type || "default";

        const titleEl = document.getElementById("confirmModalLabel");
        const messageEl = document.getElementById("confirmModalMessage");
        const confirmButton = document.getElementById("confirmModalButton");
        const modal = bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);

        if (titleEl) titleEl.textContent = title;
        if (messageEl) messageEl.textContent = message || "Are you sure you want to proceed?";
        if (confirmButton) {
            confirmButton.textContent = okText;
            confirmButton.classList.remove("btn-danger", "btn-warning", "btn-primary");
            if (type === "danger") {
                confirmButton.classList.add("btn-danger");
            } else if (type === "warning") {
                confirmButton.classList.add("btn-warning");
            } else {
                confirmButton.classList.add("btn-primary");
            }
        }

        return new Promise((resolve) => {
            let settled = false;
            let confirmed = false;
            const settle = (value) => {
                if (settled) return;
                settled = true;
                resolve(value);
            };

            confirmButton.onclick = null;
            confirmButton.onclick = function () {
                confirmed = true;
                modal.hide();
                setTimeout(() => {
                    cleanupModalBackdrops();
                    if (typeof onConfirm === "function") onConfirm();
                    settle(true);
                }, 50);
            };

            modalElement.addEventListener("hidden.bs.modal", () => {
                if (!confirmed) settle(false);
            }, { once: true });

            cleanupModalBackdrops();
            modal.show();
        });
    }

    let statusModalEl = null;

    function ensureStatusModal() {
        if (statusModalEl) return statusModalEl;
        const wrapper = document.createElement("div");
        wrapper.className = "modal fade";
        wrapper.id = "slStatusModal";
        wrapper.tabIndex = -1;
        wrapper.innerHTML = `
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content sl-status-modal">
                    <div class="modal-body text-center p-4">
                        <div class="sl-status-icon mb-3" aria-hidden="true">✓</div>
                        <h5 class="sl-status-title mb-2">Success</h5>
                        <p class="sl-status-message mb-4 text-muted-lib"></p>
                        <button type="button" class="btn btn-accent sl-status-ok">OK</button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(wrapper);
        statusModalEl = wrapper;
        return statusModalEl;
    }

    function showStatusModal(type, message) {
        const normalized = type === "error" ? "error" : "success";
        const modalElement = ensureStatusModal();
        const iconEl = modalElement.querySelector(".sl-status-icon");
        const titleEl = modalElement.querySelector(".sl-status-title");
        const messageEl = modalElement.querySelector(".sl-status-message");

        modalElement.classList.toggle("sl-status-modal--error", normalized === "error");
        if (iconEl) iconEl.textContent = normalized === "error" ? "!" : "✓";
        if (titleEl) titleEl.textContent = normalized === "error" ? "Action Failed" : "Success";
        if (messageEl) messageEl.textContent = message || (normalized === "error" ? "Something went wrong." : "Saved successfully.");

        const modal = bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);
        const okBtn = modalElement.querySelector(".sl-status-ok");
        if (okBtn) {
            okBtn.onclick = () => modal.hide();
        }
        cleanupModalBackdrops();
        modal.show();
    }

    function showFieldError(input, message) {
        if (!input) return;
        input.classList.add("is-invalid");
        const wrapper = input.closest(".input-wrapper") || input.parentElement;
        let hint = wrapper.querySelector(".field-error");
        if (!hint) {
            hint = document.createElement("small");
            hint.className = "field-error text-danger d-block mt-1";
            wrapper.appendChild(hint);
        }
        hint.textContent = message;
    }

    function clearFieldError(input) {
        if (!input) return;
        input.classList.remove("is-invalid");
        const wrapper = input.closest(".input-wrapper") || input.parentElement;
        const hint = wrapper.querySelector(".field-error");
        if (hint) hint.remove();
    }

    function validatePasswordForm(form) {
        const newPass = form.querySelector('input[name="newPassword"]');
        const confirm = form.querySelector('input[name="confirmPassword"]');
        if (!newPass || !confirm) return true;
        clearFieldError(newPass);
        clearFieldError(confirm);
        if (newPass.value.length < 6) {
            showFieldError(newPass, "Password must be at least 6 characters.");
            return false;
        }
        if (newPass.value !== confirm.value) {
            showFieldError(confirm, "Passwords do not match.");
            return false;
        }
        return true;
    }

    function validateRequiredForm(form) {
        let valid = true;
        form.querySelectorAll("[required]").forEach((input) => {
            clearFieldError(input);
            if (!input.value || !String(input.value).trim()) {
                showFieldError(input, "This field is required.");
                valid = false;
            }
        });
        return valid;
    }

    function validateEmailInputs(form) {
        let valid = true;
        form.querySelectorAll('input[type="email"]').forEach((input) => {
            clearFieldError(input);
            if (input.value && !EMAIL_RE.test(input.value.trim())) {
                showFieldError(input, "Please enter a valid email.");
                valid = false;
            }
        });
        return valid;
    }

    function setButtonLoading(button, loading) {
        if (!button) return;
        if (loading) {
            if (!button.dataset.originalHtml) {
                button.dataset.originalHtml = button.innerHTML;
            }
            button.disabled = true;
            button.classList.add("btn-loading");
            button.innerHTML = '<span class="loading-spinner" aria-hidden="true"></span><span>Please wait...</span>';
        }
    }

    function applyFormLoading(form) {
        const btn = form.querySelector('button[type="submit"]:not([disabled])');
        if (btn) setButtonLoading(btn, true);
        form.classList.add("is-submitting");
    }

    function initLoginUsernameRestore() {
        const loginForm = document.querySelector('form[action*="/login"]');
        if (!loginForm) return;
        const usernameInput = loginForm.querySelector('input[name="username"]');
        if (!usernameInput) return;
        const storageKey = "sl_login_username";
        const hasLoginError = Boolean(document.querySelector(".alert.alert-danger"));
        if (hasLoginError) {
            const saved = sessionStorage.getItem(storageKey);
            if (saved) usernameInput.value = saved;
        } else {
            sessionStorage.removeItem(storageKey);
        }
        loginForm.addEventListener("submit", () => {
            const value = usernameInput.value.trim();
            if (value) sessionStorage.setItem(storageKey, value);
        });
    }

    function initEmptyStates() {
        document.querySelectorAll("table tbody tr").forEach((row) => {
            const cell = row.querySelector("td[colspan]");
            if (!cell || row.children.length !== 1) return;
            const text = cell.textContent.trim().toLowerCase();
            if (text.includes("no ") || text.includes("walang") || text.includes("yet") || text.includes("empty")) {
                row.classList.add("empty-state-row");
                cell.classList.add("empty-state-cell");
            }
        });
    }

    let sessionToastEl = null;

    function hideSessionToast() {
        if (sessionToastEl) sessionToastEl.classList.remove("show");
    }

    function showSessionToast(message, isExpired) {
        if (!sessionToastEl) {
            sessionToastEl = document.createElement("div");
            sessionToastEl.className = "session-timeout-toast";
            sessionToastEl.setAttribute("role", "alert");
            sessionToastEl.innerHTML = '<span class="session-timeout-icon" aria-hidden="true">⏱</span><span class="session-timeout-text"></span>';
            document.body.appendChild(sessionToastEl);
        }
        sessionToastEl.querySelector(".session-timeout-text").textContent = message;
        sessionToastEl.classList.toggle("session-timeout-toast--expired", Boolean(isExpired));
        sessionToastEl.classList.add("show");
    }

    function initSessionTimeout() {
        if (isAuthPage()) return;
        const isLoggedIn = document.querySelector("#logoutBtn, #logoutForm, .admin-sidebar, #studentSidebar");
        if (!isLoggedIn) return;

        const timeoutMs = (Number(document.body.dataset.sessionMinutes) || 30) * 60 * 1000;
        const warningMs = 2 * 60 * 1000;
        let expiry = Date.now() + timeoutMs;
        let warned = false;

        const resetTimer = () => {
            expiry = Date.now() + timeoutMs;
            warned = false;
            hideSessionToast();
        };

        ["click", "keydown", "mousemove", "scroll", "touchstart"].forEach((evt) => {
            document.addEventListener(evt, resetTimer, { passive: true });
        });

        setInterval(() => {
            const remaining = expiry - Date.now();
            if (remaining <= 0) {
                showSessionToast("Your session has expired. Redirecting to login...", true);
                const loginPath = "/login?error=session";
                setTimeout(() => { window.location.href = loginPath; }, 2500);
            } else if (remaining <= warningMs && !warned) {
                warned = true;
                showSessionToast("Your session will expire in 2 minutes due to inactivity.");
            }
        }, 10000);
    }

    document.addEventListener("submit", (event) => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) return;

        if (form.classList.contains("confirm-delete") || form.hasAttribute("data-confirm-delete")) {
            event.preventDefault();
            const msg = form.getAttribute("data-confirm-message")
                || "Are you sure you want to delete this record? This action cannot be undone.";
            confirmAction(msg, { type: "danger", title: "Confirm Delete", okText: "Yes, Delete" }).then((confirmed) => {
                if (confirmed) {
                    applyFormLoading(form);
                    form.submit();
                }
            });
            return;
        }

        if (form.classList.contains("confirm-action") || form.hasAttribute("data-confirm")) {
            event.preventDefault();
            const msg = form.getAttribute("data-confirm-message") || "Are you sure you want to proceed?";
            confirmAction(msg, { type: "warning", title: "Confirm Action", okText: "Yes, Proceed" }).then((confirmed) => {
                if (confirmed) {
                    applyFormLoading(form);
                    form.submit();
                }
            });
            return;
        }

        if (form.classList.contains("sl-validate") || form.hasAttribute("data-validate")) {
            if (!validateRequiredForm(form)) {
                event.preventDefault();
                return;
            }
            if (!validateEmailInputs(form)) {
                event.preventDefault();
                return;
            }
            if (form.querySelector('input[name="newPassword"]') && !validatePasswordForm(form)) {
                event.preventDefault();
                return;
            }
            const confirmPassword = form.querySelector('input[name="confirmPassword"]');
            const password = form.querySelector('input[name="password"]');
            if (confirmPassword && password && password.value !== confirmPassword.value) {
                showFieldError(confirmPassword, "Passwords do not match.");
                event.preventDefault();
                return;
            }
        }

        if (!event.defaultPrevented && form.dataset.noLoading !== "true") {
            applyFormLoading(form);
        }
    });

    document.addEventListener("DOMContentLoaded", () => {
        const logoutBtn = document.getElementById("logoutBtn");
        const logoutForm = document.getElementById("logoutForm");
        if (logoutBtn && logoutForm) {
            logoutBtn.addEventListener("click", () => {
                confirmAction("Are you sure you want to log out?", {
                    type: "warning",
                    title: "Log Out",
                    okText: "Yes, Log Out",
                }).then((confirmed) => {
                    if (confirmed) logoutForm.submit();
                });
            });
        }

        document.querySelectorAll('form[action*="/delete"], form[action*="/delete-history"]').forEach((form) => {
            form.classList.add("confirm-delete");
        });

        document.querySelectorAll('form[action*="/account/password"], form[action*="/change-password"]').forEach((form) => {
            form.classList.add("sl-validate");
            if (form.querySelector('input[name="newPassword"]')) {
                form.classList.add("confirm-action");
                form.setAttribute("data-confirm-message", "Change your password?");
            }
        });

        document.querySelectorAll('form[action*="/forgot-password"]').forEach((form) => {
            if (!form.action.includes("send-otp")) {
                form.classList.add("sl-validate");
            }
        });

        document.querySelectorAll(".modal").forEach((modalElement) => {
            modalElement.addEventListener("hidden.bs.modal", cleanupModalBackdrops);
        });
        cleanupModalBackdrops();
    });

    const authPage = isAuthPage();
    const successAlert = document.querySelector(".alert.alert-success");
    const errorAlert = document.querySelector(".alert.alert-danger");
    if (successAlert && successAlert.textContent.trim() && !authPage) {
        showStatusModal("success", successAlert.textContent.trim());
        successAlert.style.display = "none";
    } else if (errorAlert && errorAlert.textContent.trim() && !authPage) {
        showStatusModal("error", errorAlert.textContent.trim());
        errorAlert.style.display = "none";
    }

    initLoginUsernameRestore();
    initEmptyStates();
    initSessionTimeout();

    window.confirmAction = confirmAction;
    window.slShowStatus = showStatusModal;
    window.cleanupModalBackdrops = cleanupModalBackdrops;
})();
