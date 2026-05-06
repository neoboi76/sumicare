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
      { path: 'services', loadComponent: () => import('./features/public/services.component').then(m => m.ServicesComponent) },
      { path: 'recommendation', loadComponent: () => import('./features/public/recommendation.component').then(m => m.RecommendationComponent) },
      { path: 'book', loadComponent: () => import('./features/public/book.component').then(m => m.BookComponent) },
      { path: 'feedback', loadComponent: () => import('./features/public/feedback.component').then(m => m.FeedbackComponent) }
    ]
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
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
      { path: 'decking', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/decking/decking.component').then(m => m.DeckingComponent) },
      { path: 'pos', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/pos/pos.component').then(m => m.PosComponent) },
      { path: 'treatment-slips', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/treatment-slips/treatment-slips.component').then(m => m.TreatmentSlipsComponent) },
      { path: 'treatment-slips/:id', canActivate: [roleGuard(STAFF_ROLES)], loadComponent: () => import('./features/internal/treatment-slips/treatment-slip-detail.component').then(m => m.TreatmentSlipDetailComponent) },
      { path: 'reports', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/reports/reports.component').then(m => m.ReportsComponent) },
      { path: 'users', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/users.component').then(m => m.UsersComponent) },
      { path: 'audit', canActivate: [roleGuard(ADMIN_PLUS)], loadComponent: () => import('./features/internal/admin/audit.component').then(m => m.AuditComponent) },
      { path: 'branding', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/branding.component').then(m => m.BrandingComponent) },
      { path: 'admin/therapists', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/therapists.component').then(m => m.TherapistsAdminComponent) },
      { path: 'admin/shifts', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/shifts.component').then(m => m.ShiftsAdminComponent) },
      { path: 'admin/rooms', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/rooms.component').then(m => m.RoomsAdminComponent) },
      { path: 'admin/services', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/services.component').then(m => m.ServicesAdminComponent) },
      { path: 'admin/vouchers', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/vouchers.component').then(m => m.VouchersAdminComponent) },
      { path: 'admin/feedback', canActivate: [roleGuard(MANAGER_PLUS)], loadComponent: () => import('./features/internal/admin/feedback.component').then(m => m.FeedbackAdminComponent) }
    ]
  },
  { path: '**', redirectTo: '' }
];
