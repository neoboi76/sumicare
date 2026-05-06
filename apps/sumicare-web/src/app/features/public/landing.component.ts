import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface ContentBlock {
  id: string;
  sectionKey: string;
  title: string;
  body: string;
  imageUrl: string | null;
}

@Component({
  selector: 'sumi-landing',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './landing.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingComponent implements OnInit {
  private http = inject(HttpClient);
  blocks = signal<ContentBlock[]>([]);

  ngOnInit(): void {
    this.http
      .get<ContentBlock[]>(`${environment.apiBaseUrl}/api/public/content/${environment.defaultOrganizationSlug}`)
      .subscribe({
        next: (blocks) => this.blocks.set(blocks),
        error: () => this.blocks.set([])
      });
  }
}
