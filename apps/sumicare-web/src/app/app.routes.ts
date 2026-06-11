import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth.guard';

const STAFF_ROLES = ['RECEPTIONIST', 'MANAGER', 'ADMIN', 'SUPERADMIN'];
const MANAGER_PLUS = ['MANAGER', 'ADMIN', 'SUPERADMIN'];
const ADMIN_PLUS = ['ADMIN', 'SUPERADMIN'];

export const APP_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/public/public-shell.component').then(m => m.PublicShellComponent),
    children: [
      { path: '', loadComponent: () => import('./features/public/landing.component').then(m => m.LandingComponent) },
      { path: 'about', loadComponent: () => import('./features/public/about.component').then(m => m.AboutComponent) },
      { path: 'packages', loadComponent: () => import('./features/public/packages.component').then(m => m.PackagesComponent) },
      { path: 'services', loadComponent: () => import('./features/public/services.component').then(m => m.ServicesComponent) },
      { path: 'recommendation', loadComponent: () => import('./features/public/recommendation.component').then(m => m.RecommendationComponent) },
      { path: 'book', loadComponent: () => import('./features/public/book.component').then(m => m.BookComponent) },
      { path: 'visit', loadComponent: () => import('./features/public/visit.component').then(m => m.VisitComponent) },
      { path: 'feedback', loadComponent: () => import('./features/public/feedback.component').then(m => m.FeedbackComponent) },
      { path: 'contact', loadComponent: () => import('./features/public/contact.component').then(m => m.ContactComponent) },
      { path: 'cancel', loadComponent: () => import('./features/public/cancel.component').then(m => m.CancelComponent) },
      { path: 'terms', loadComponent: () => import('./features/public/terms.component').then(m => m.TermsComponent) }
    ]
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'verify',
    loadComponent: () => import('./features/auth/verify.component').then(m => m.VerifyComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./features/auth/reset-password.component').then(m => m.ResetPasswordComponent)
  },
  {
    path: 'invite',
    loadComponent: () => import('./features/auth/invite.component').then(m => m.InviteComponent)
  },
  {
    path: 'pay/authorize',
    loadComponent: () => import('./features/public/paymongo-authorize.component').then(m => m.PaymongoAuthorizeComponent)
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./features/internal/internal-shell.component').then(m => m.InternalShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./features/internal/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'bookings', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/bookings/bookings.component').then(m => m.BookingsComponent) },
      { path: 'reception', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/reception/reception.component').then(m => m.ReceptionComponent) },
      { path: 'lineup', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/lineup/lineup.component').then(m => m.LineupComponent) },
      { path: 'cashier', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/cashier/cashier.component').then(m => m.CashierComponent) },
      { path: 'pos', redirectTo: 'cashier' },
      { path: 'orders', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/orders/orders-list.component').then(m => m.OrdersListComponent) },
      { path: 'orders/:id', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/orders/order-detail.component').then(m => m.OrderDetailComponent) },
      { path: 'messages', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/messages/messages.component').then(m => m.MessagesComponent) },
      { path: 'registered-clients', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/registered-clients/registered-clients.component').then(m => m.RegisteredClientsComponent) },
      { path: 'treatment-slips', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/treatment-slips/treatment-slips.component').then(m => m.TreatmentSlipsComponent) },
      { path: 'treatment-slips/:id', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/treatment-slips/treatment-slip-detail.component').then(m => m.TreatmentSlipDetailComponent) },
      { path: 'reports', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/reports/reports.component').then(m => m.ReportsComponent) },
      { path: 'attendance', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/attendance/attendance.component').then(m => m.AttendanceComponent) },
      { path: 'ledger', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/ledger/ledger.component').then(m => m.LedgerComponent) },
      { path: 'analytics', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/analytics/analytics.component').then(m => m.AnalyticsComponent) },
      { path: 'settings', loadComponent: () => import('./features/internal/settings/settings.component').then(m => m.SettingsComponent) },
      { path: 'users', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/users.component').then(m => m.UsersComponent) },
      { path: 'audit', canActivate: [roleGuard(ADMIN_PLUS)], loadComponent: () => import('./features/internal/admin/audit.component').then(m => m.AuditComponent) },
      { path: 'branding', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/branding.component').then(m => m.BrandingComponent) },
      { path: 'content', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/content/content.component').then(m => m.ContentComponent) },
      { path: 'admin/therapists', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/therapists.component').then(m => m.TherapistsAdminComponent) },
      { path: 'admin/shifts', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/shifts.component').then(m => m.ShiftsAdminComponent) },
      { path: 'admin/rooms', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/rooms.component').then(m => m.RoomsAdminComponent) },
      { path: 'admin/services', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/services.component').then(m => m.ServicesAdminComponent) },
      { path: 'admin/packages', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/packages.component').then(m => m.PackagesAdminComponent) },
      { path: 'admin/vouchers', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/vouchers.component').then(m => m.VouchersAdminComponent) },
      { path: 'admin/feedback', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/feedback.component').then(m => m.FeedbackAdminComponent) }
    ]
  },
  { path: '**', redirectTo: '' }
];
