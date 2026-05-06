import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const session = auth.session();
  const isApi = req.url.includes('/api/');
  const authedRequest = session && isApi
    ? req.clone({ setHeaders: { Authorization: `Bearer ${session.accessToken}` } })
    : req;
  return next(authedRequest).pipe(
    catchError((error) => {
      if (error.status === 401 && session && !req.url.endsWith('/api/auth/refresh')) {
        return auth.refresh().pipe(
          switchMap(() => {
            const refreshed = auth.session();
            const retried = refreshed
              ? req.clone({ setHeaders: { Authorization: `Bearer ${refreshed.accessToken}` } })
              : req;
            return next(retried);
          }),
          catchError(() => {
            router.navigate(['/login']);
            return throwError(() => error);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
