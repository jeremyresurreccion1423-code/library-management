(function () {
  function getCsrfToken() {
    const meta = document.querySelector('meta[name="_csrf"]');
    return meta ? meta.getAttribute('content') : null;
  }

  function submitBulkForm(action, ids, confirmMessage, confirmOptions) {
    if (!ids.length) {
      alert('Select at least one item.');
      return;
    }

    const submit = function () {
      const form = document.createElement('form');
      form.method = 'post';
      form.action = action;

      const csrf = getCsrfToken();
      if (csrf) {
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = '_csrf';
        csrfInput.value = csrf;
        form.appendChild(csrfInput);
      }

      ids.forEach(function (id) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'ids';
        input.value = id;
        form.appendChild(input);
      });

      document.body.appendChild(form);
      form.submit();
    };

    if (typeof confirmAction === 'function') {
      confirmAction(confirmMessage, confirmOptions || { type: 'warning' }, submit);
    } else if (confirm(confirmMessage)) {
      submit();
    }
  }

  function initBulkTable(config) {
    const table = document.getElementById(config.tableId);
    if (!table) return;

    const selectAll = document.getElementById(config.selectAllId);
    const bulkButtons = (config.bulkActions || []).map(function (action) {
      return {
        button: document.getElementById(action.buttonId),
        action: action.action,
        selector: action.selector,
        message: action.message,
        confirmOptions: action.confirmOptions || { type: 'warning' }
      };
    });

    function bulkRowElement(checkbox) {
      return checkbox.closest('tr')
        || checkbox.closest('.sl-book-card')
        || checkbox.closest('[data-issue-id]');
    }

    function rowCheckboxes() {
      const tbodyBoxes = Array.from(table.querySelectorAll('tbody input[type="checkbox"].bulk-row-check, tbody input[type="checkbox"].bulk-select-check.bulk-row-check'));
      if (tbodyBoxes.length) {
        return tbodyBoxes;
      }
      return Array.from(table.querySelectorAll('input[type="checkbox"].bulk-row-check, input[type="checkbox"].bulk-select-check.bulk-row-check'));
    }

    function selectedCheckboxes(filterSelector) {
      return rowCheckboxes().filter(function (cb) {
        if (!cb.checked) return false;
        if (!filterSelector) return true;
        const row = bulkRowElement(cb);
        return row && row.matches(filterSelector);
      });
    }

    function syncSelectAll() {
      if (!selectAll) return;
      const boxes = rowCheckboxes();
      if (!boxes.length) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
        return;
      }
      const checkedCount = boxes.filter(function (cb) { return cb.checked; }).length;
      selectAll.checked = checkedCount === boxes.length;
      selectAll.indeterminate = checkedCount > 0 && checkedCount < boxes.length;
    }

    if (selectAll) {
      selectAll.addEventListener('change', function () {
        rowCheckboxes().forEach(function (cb) {
          cb.checked = selectAll.checked;
        });
        selectAll.indeterminate = false;
      });
    }

    table.addEventListener('change', function (event) {
      if (event.target && event.target.classList.contains('bulk-row-check')) {
        syncSelectAll();
      }
    });

    bulkButtons.forEach(function (entry) {
      if (!entry.button) return;
      entry.button.addEventListener('click', function () {
        const ids = selectedCheckboxes(entry.selector).map(function (cb) {
          return cb.value;
        });
        submitBulkForm(entry.action, ids, entry.message, entry.confirmOptions);
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (window.libraryBulkTables && Array.isArray(window.libraryBulkTables)) {
      window.libraryBulkTables.forEach(initBulkTable);
    }
  });

  if (document.readyState !== 'loading') {
    if (window.libraryBulkTables && Array.isArray(window.libraryBulkTables)) {
      window.libraryBulkTables.forEach(initBulkTable);
    }
  }
})();
