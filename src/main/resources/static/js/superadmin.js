/**
 * EduLibrary — Super Admin portal (purple executive theme, isolated from Admin UI)
 */
(function () {
  'use strict';

  window.saToggleSidebarGroup = function (button) {
    var group = button && button.closest ? button.closest('.sa-sidebar-group') : null;
    if (!group) return;
    group.classList.toggle('open');
    button.setAttribute('aria-expanded', group.classList.contains('open') ? 'true' : 'false');
  };

  window.saToggleSidebar = function () {
    var sidebar = document.getElementById('saSidebar');
    var main = document.querySelector('.sa-dashboard-main');
    if (!sidebar) return;
    sidebar.classList.toggle('collapsed');
    if (main) main.classList.toggle('expanded');
  };

  window.saToggleProfileMenu = function (button) {
    var widget = button.closest('.sa-profile-widget');
    var menu = widget ? widget.querySelector('.sa-profile-menu') : null;
    if (!menu) return;
    var shouldOpen = !menu.classList.contains('show');
    document.querySelectorAll('.sa-profile-menu.show').forEach(function (m) {
      m.classList.remove('show');
    });
    if (shouldOpen) menu.classList.add('show');
  };

  window.closeAllProfileMenus = function () {
    document.querySelectorAll('.sa-profile-menu.show').forEach(function (m) {
      m.classList.remove('show');
    });
  };

  function initSidebarActiveLinks() {
    var currentPath = window.location.pathname;
    document.querySelectorAll('.sa-sidebar-link, .sa-sidebar-sublink').forEach(function (link) {
      var prefix = link.dataset.navPrefix || '';
      if (!prefix) return;
      var exact = link.dataset.navExact === 'true';
      var exclude = link.dataset.navExclude || '';
      if (exclude && currentPath.startsWith(exclude)) return;
      var isActive = exact ? currentPath === prefix : currentPath.startsWith(prefix);
      if (isActive) {
        link.classList.add('active');
        var group = link.closest('.sa-sidebar-group');
        if (group) {
          group.classList.add('open');
          var btn = group.querySelector('.sa-sidebar-group-btn');
          if (btn) btn.setAttribute('aria-expanded', 'true');
        }
      }
    });
    document.querySelectorAll('.sa-sidebar-group.open .sa-sidebar-group-btn').forEach(function (btn) {
      btn.setAttribute('aria-expanded', 'true');
    });
  }

  function initOverviewChart() {
    var canvas = document.getElementById('saOverviewChart');
    if (!canvas || typeof Chart === 'undefined' || !canvas.dataset) return;
    var attStudents = Number(canvas.dataset.attStudents || 0);
    var attSubjects = Number(canvas.dataset.attSubjects || 0);
    var attToday = Number(canvas.dataset.attToday || 0);
    var libBooks = Number(canvas.dataset.libBooks || 0);
    var libStudents = Number(canvas.dataset.libStudents || 0);
    var libLoans = Number(canvas.dataset.libLoans || 0);
    new Chart(canvas, {
      type: 'bar',
      data: {
        labels: ['Students (Att.)', 'Subjects', 'Today Att.', 'Books', 'Students (Lib.)', 'Active Loans'],
        datasets: [{
          label: 'System Metrics',
          data: [attStudents, attSubjects, attToday, libBooks, libStudents, libLoans],
          backgroundColor: ['rgba(59,130,246,0.75)','rgba(96,165,250,0.75)','rgba(37,99,235,0.85)','rgba(124,58,237,0.75)','rgba(139,92,246,0.75)','rgba(109,40,217,0.85)'],
          borderRadius: 10, borderSkipped: false
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { display: false }, tooltip: { backgroundColor: '#1E1B4B', padding: 12, cornerRadius: 10 } },
        scales: {
          x: { grid: { display: false }, ticks: { font: { family: 'Plus Jakarta Sans', size: 11 }, color: '#64748B' } },
          y: { beginAtZero: true, grid: { color: 'rgba(109,40,217,0.06)' }, ticks: { font: { family: 'Plus Jakarta Sans' }, color: '#64748B' } }
        }
      }
    });
  }

  function initAnalyticsCharts() {
    if (typeof Chart === 'undefined') return;
    var attCanvas = document.getElementById('saAttendanceChart');
    var libCanvas = document.getElementById('saLibraryChart');
    if (attCanvas && attCanvas.dataset) {
      new Chart(attCanvas, {
        type: 'doughnut',
        data: {
          labels: ['Today Present', 'Low Alerts', 'Remaining'],
          datasets: [{ data: [Number(attCanvas.dataset.attToday||0), Number(attCanvas.dataset.attLow||0), Math.max(0, Number(attCanvas.dataset.attStudents||0)-Number(attCanvas.dataset.attToday||0))], backgroundColor: ['#8B5CF6','#F59E0B','#E2E8F0'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, cutout: '68%', plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } } } }
      });
    }
    if (libCanvas && libCanvas.dataset) {
      new Chart(libCanvas, {
        type: 'doughnut',
        data: {
          labels: ['Active Loans', 'Overdue', 'Catalog'],
          datasets: [{ data: [Number(libCanvas.dataset.libLoans||0), Number(libCanvas.dataset.libOverdue||0), Number(libCanvas.dataset.libBooks||0)], backgroundColor: ['#7C3AED','#EF4444','#C4B5FD'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, cutout: '68%', plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } } } }
      });
    }
  }

  document.addEventListener('click', function (e) {
    if (!e.target.closest('.sa-profile-widget')) closeAllProfileMenus();
    var sidebar = document.getElementById('saSidebar');
    var toggle = document.querySelector('.sa-header-bar-toggle');
    if (sidebar && !sidebar.classList.contains('collapsed') && window.innerWidth < 992) {
      if (!sidebar.contains(e.target) && toggle && e.target !== toggle && !toggle.contains(e.target)) {
        sidebar.classList.add('collapsed');
        var main = document.querySelector('.sa-dashboard-main');
        if (main) main.classList.add('expanded');
      }
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    initSidebarActiveLinks();
    initOverviewChart();
    initAnalyticsCharts();
  });
})();
