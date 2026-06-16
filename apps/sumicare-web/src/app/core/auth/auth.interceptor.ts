/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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
        // auth.refresh() is single-flight, so concurrent 401s queue on one shared
        // refresh rather than each firing its own; once it completes every queued
        // request retries with the new token, and a refresh failure falls through
        // to the catchError below which redirects to login.
        return auth.refresh().pipe(
          switchMap(() => {
            const refreshed = auth.session();
            const retried = refreshed
              ? req.clone({ setHeaders: { Authorization: `Bearer ${refreshed.accessToken}` } })
              : req;
            return next(retried);
          }),
          catchError(() => {
            router.navigate(['/sumicare/login']);
            return throwError(() => error);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
