import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../../shared/components/toast/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 0) {
        toast.error('Cannot reach the server. Check your connection and try again.');
      } else if (error.status === 408 || error.status === 504) {
        toast.error('The request timed out. Please try again.');
      } else if (error.status >= 500) {
        toast.error('Something went wrong on our end. Please try again in a moment.');
      }
      return throwError(() => error);
    })
  );
};
