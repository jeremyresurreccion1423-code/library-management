/**
 * EduLibrary — Super Admin portal (isolated from Admin / Student UI)
 */
(function () {
  'use strict';

  window.saToggleGroup = function (groupId) {
    var group = document.getElementById(groupId);
    if (!group) return;
    group.classList.toggle('sa-nav-group--open');
    var btn = group.querySelector('.sa-nav-group__btn');
    if (btn) {
      btn.setAttribute('aria-expanded', group.classList.contains('sa-nav-group--open') ? 'true' : 'false');
    }
  };

  window.saToggleSidebar = function () {
    document.body.classList.toggle('sa-sidebar-collapsed');
    document.body.classList.toggle('sa-sidebar-mobile-open');
  };

  window.saCloseSidebar = function () {
    document.body.classList.remove('sa-sidebar-mobile-open');
  };

  window.saToggleProfileMenu = function (button) {
    var widget = button.closest('.sa-navbar-profile');
    var menu = widget ? widget.querySelector('.sa-navbar-profile__menu') : null;
    if (!menu) return;
    var shouldOpen = !menu.classList.contains('is-open');
    document.querySelectorAll('.sa-navbar-profile__menu.is-open').forEach(function (m) {
      m.classList.remove('is-open');
    });
    if (shouldOpen) menu.classList.add('is-open');
  };

  function initSidebarActiveLinks() {
    var currentPath = window.location.pathname;
    document.querySelectorAll('.sa-sublink').forEach(function (link) {
      var prefix = link.dataset.navPrefix || '';
      if (!prefix) return;
      var exact = link.dataset.navExact === 'true';
      var exclude = link.dataset.navExclude || '';
      if (exclude && currentPath.startsWith(exclude)) return;
      var isActive = exact ? currentPath === prefix : currentPath.startsWith(prefix);
      if (isActive) {
        link.classList.add('sa-sublink--active');
        var group = link.closest('.sa-nav-group');
        if (group) {
          group.classList.add('sa-nav-group--open');
          var btn = group.querySelector('.sa-nav-group__btn');
          if (btn) btn.setAttribute('aria-expanded', 'true');
        }
      }
    });

    document.querySelectorAll('.sa-nav-group.sa-nav-group--open .sa-nav-group__btn').forEach(function (btn) {
      btn.setAttribute('aria-expanded', 'true');
    });
  }

  function initLucide() {
    if (window.lucide && typeof window.lucide.createIcons === 'function') {
      window.lucide.createIcons();
    }
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
          backgroundColor: [
            'rgba(59, 130, 246, 0.75)',
            'rgba(96, 165, 250, 0.75)',
            'rgba(37, 99, 235, 0.85)',
            'rgba(124, 58, 237, 0.75)',
            'rgba(147, 51, 234, 0.75)',
            'rgba(109, 40, 217, 0.85)'
          ],
          borderRadius: 10,
          borderSkipped: false
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1E1B4B',
            padding: 12,
            cornerRadius: 10
          }
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { font: { family: 'Plus Jakarta Sans', size: 11 }, color: '#64748B' }
          },
          y: {
            beginAtZero: true,
            grid: { color: 'rgba(109, 40, 217, 0.06)' },
            ticks: { font: { family: 'Plus Jakarta Sans' }, color: '#64748B' }
          }
        }
      }
    });
  }

  function initAnalyticsCharts() {
    var attCanvas = document.getElementById('saAttendanceChart');
    var libCanvas = document.getElementById('saLibraryChart');
    if (typeof Chart === 'undefined') return;

    if (attCanvas && attCanvas.dataset) {
      new Chart(attCanvas, {
        type: 'doughnut',
        data: {
          labels: ['Today Present', 'Low Attendance Alerts', 'Remaining Students'],
          datasets: [{
            data: [
              Number(attCanvas.dataset.attToday || 0),
              Number(attCanvas.dataset.attLow || 0),
              Math.max(0, Number(attCanvas.dataset.attStudents || 0) - Number(attCanvas.dataset.attToday || 0))
            ],
            backgroundColor: ['#3B82F6', '#F59E0B', '#E2E8F0'],
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '68%',
          plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } } }
        }
      });
    }

    if (libCanvas && libCanvas.dataset) {
      new Chart(libCanvas, {
        type: 'doughnut',
        data: {
          labels: ['Active Loans', 'Overdue', 'Available Catalog'],
          datasets: [{
            data: [
              Number(libCanvas.dataset.libLoans || 0),
              Number(libCanvas.dataset.libOverdue || 0),
              Number(libCanvas.dataset.libBooks || 0)
            ],
            backgroundColor: ['#7C3AED', '#EF4444', '#C4B5FD'],
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '68%',
          plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } } }
        }
      });
    }
  }

  document.addEventListener('click', function (e) {
    if (!e.target.closest('.sa-navbar-profile')) {
      document.querySelectorAll('.sa-navbar-profile__menu.is-open').forEach(function (m) {
        m.classList.remove('is-open');
      });
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    initLucide();
    initSidebarActiveLinks();
    initOverviewChart();
    initAnalyticsCharts();
  });
})();
