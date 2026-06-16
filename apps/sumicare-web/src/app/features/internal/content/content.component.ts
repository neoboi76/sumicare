/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';

interface ContentBlock {
  id: string;
  sectionKey: string;
  title: string;
  body: string;
  imageUrl: string | null;
  displayOrder: number;
  published: boolean;
  updatedAt: string;
}

@Component({
  selector: 'sumi-content',
  templateUrl: './content.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule]
})
export class ContentComponent implements OnInit {
  private http = inject(HttpClient);

  blocks = signal<ContentBlock[]>([]);
  loading = signal(false);
  savingId = signal<string | null>(null);
  addingNew = signal(false);
  newBlock: Partial<ContentBlock> = { sectionKey: '', title: '', body: '', published: true };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<ContentBlock[]>(`${environment.apiBaseUrl}/api/content`).subscribe({
      next: (b) => { this.blocks.set(b); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  save(block: ContentBlock): void {
    this.savingId.set(block.id);
    this.http.put<ContentBlock>(`${environment.apiBaseUrl}/api/content/blocks/${block.id}`, block).subscribe({
      next: (updated) => {
        this.blocks.update(list => list.map(b => b.id === updated.id ? updated : b));
        this.savingId.set(null);
      },
      error: () => this.savingId.set(null)
    });
  }

  createBlock(): void {
    this.http.post<ContentBlock>(`${environment.apiBaseUrl}/api/content`, this.newBlock).subscribe({
      next: (b) => {
        this.blocks.update(list => [...list, b]);
        this.newBlock = { sectionKey: '', title: '', body: '', published: true };
        this.addingNew.set(false);
      }
    });
  }

  uploadImage(event: Event, block: ContentBlock): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    this.http.post<{ url: string }>(`${environment.apiBaseUrl}/api/content/upload`, form).subscribe({
      next: (res) => {
        const updated = { ...block, imageUrl: res.url };
        this.blocks.update(list => list.map(b => b.id === block.id ? updated : b));
        this.save(updated as ContentBlock);
      }
    });
  }
}
