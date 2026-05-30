import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../core/auth/auth.service';
import { IdleTimeoutService } from '../../core/auth/idle-timeout.service';
import { BrandingService } from '../../core/branding/branding.service';
import { ConfirmService } from '../../shared/components/confirm-dialog/confirm.service';
import { routeFade } from '../../shared/animations/route-fade';
import { StompService } from '../../core/realtime/stomp.service';
import { NotificationFeedService, NotificationKey } from '../../core/notifications/notification-feed.service';
import { NotificationToastComponent } from '../../shared/components/notification-toast/notification-toast.component';

interface NavItem {
  label: string;
  route: string;
  roles: string[];
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const STAFF_ROLES = ['RECEPTIONIST', 'MANAGER', 'ADMIN', 'SUPERADMIN'];
const MANAGER_PLUS = ['MANAGER', 'ADMIN', 'SUPERADMIN'];
const ADMIN_PLUS = ['ADMIN', 'SUPERADMIN'];

@Component({
  selector: 'sumi-internal-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationToastComponent],
  templateUrl: './internal-shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [routeFade]
})
export class InternalShellComponent implements OnInit, OnDestroy {
  private auth = inject(AuthService);
  private router = inject(Router);
  protected branding = inject(BrandingService);
  private confirmService = inject(ConfirmService);
  private idleTimeout = inject(IdleTimeoutService);
  private stomp = inject(StompService);
  protected feed = inject(NotificationFeedService);
  private destroyRef = inject(DestroyRef);
  session = this.auth.session;

  readonly sidebarOpen = signal(true);
  readonly routeToken = signal(0);

  groups: NavGroup[] = [
    {
      title: 'Overview',
      items: [{ label: 'Dashboard', route: 'dashboard', roles: STAFF_ROLES }]
    },
    {
      title: 'Operations',
      items: [
        { label: 'Bookings', route: 'bookings', roles: STAFF_ROLES },
        { label: 'Room map', route: 'reception', roles: STAFF_ROLES },
        { label: 'Lineup', route: 'lineup', roles: STAFF_ROLES },
        { label: 'Treatment slips', route: 'treatment-slips', roles: STAFF_ROLES },
        { label: 'Cashier', route: 'cashier', roles: STAFF_ROLES },
        { label: 'Orders', route: 'orders', roles: STAFF_ROLES },
        { label: 'Registered clients', route: 'registered-clients', roles: STAFF_ROLES },
        { label: 'Messages', route: 'messages', roles: STAFF_ROLES }
      ]
    },
    {
      title: 'Finance',
      items: [
        { label: 'Reports', route: 'reports', roles: MANAGER_PLUS },
        { label: 'Ledger', route: 'ledger', roles: MANAGER_PLUS }
      ]
    },
    {
      title: 'Configure',
      items: [
        { label: 'Therapists', route: 'admin/therapists', roles: MANAGER_PLUS },
        { label: 'Shifts', route: 'admin/shifts', roles: MANAGER_PLUS },
        { label: 'Rooms', route: 'admin/rooms', roles: MANAGER_PLUS },
        { label: 'Services', route: 'admin/services', roles: MANAGER_PLUS },
        { label: 'Packages', route: 'admin/packages', roles: MANAGER_PLUS },
        { label: 'Vouchers', route: 'admin/vouchers', roles: MANAGER_PLUS },
        { label: 'Feedback', route: 'admin/feedback', roles: MANAGER_PLUS },
        { label: 'Branding', route: 'branding', roles: MANAGER_PLUS }
      ]
    },
    {
      title: 'Administration',
      items: [
        { label: 'Settings', route: 'settings', roles: STAFF_ROLES },
        { label: 'Users', route: 'users', roles: MANAGER_PLUS },
        { label: 'Audit log', route: 'audit', roles: ADMIN_PLUS }
      ]
    }
  ];

  visibleGroups = computed(() => {
    const role = this.session()?.role;
    if (!role) return [];
    return this.groups
      .map(g => ({ ...g, items: g.items.filter(i => i.roles.includes(role)) }))
      .filter(g => g.items.length > 0);
  });

  ngOnInit(): void {
    this.branding.loadCurrentBranding();
    const saved = localStorage.getItem('sumi_sidebar_open');
    if (saved !== null) {
      this.sidebarOpen.set(saved === 'true');
    }
    this.idleTimeout.start();
    this.stomp.connect(this.session()?.accessToken ?? null);
    this.feed.start(this.auth.organizationId());
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        this.routeToken.update(v => v + 1);
        this.clearUnreadFor((event as NavigationEnd).urlAfterRedirects ?? '');
      });
  }

  ngOnDestroy(): void {
    this.idleTimeout.stop();
    this.feed.stop();
    this.stomp.disconnect();
  }

  unreadFor(route: string): number {
    if (route.startsWith('bookings')) return this.feed.unreadFor('bookings');
    if (route.startsWith('orders')) return this.feed.unreadFor('orders');
    if (route.startsWith('messages')) return this.feed.unreadFor('messages');
    if (route.startsWith('admin/feedback')) return this.feed.unreadFor('feedback');
    return 0;
  }

  private clearUnreadFor(url: string): void {
    if (url.includes('/app/bookings')) this.feed.markRead('bookings');
    else if (url.includes('/app/orders')) this.feed.markRead('orders');
    else if (url.includes('/app/messages')) this.feed.markRead('messages');
    else if (url.includes('/app/admin/feedback')) this.feed.markRead('feedback');
  }

  toggleSidebar(): void {
    this.sidebarOpen.update(v => {
      const next = !v;
      localStorage.setItem('sumi_sidebar_open', String(next));
      return next;
    });
  }

  async signOut(): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Sign out',
      message: 'Are you sure you want to sign out of SumiCare?',
      confirmText: 'Sign out'
    });
    if (!confirmed) return;
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }
}
