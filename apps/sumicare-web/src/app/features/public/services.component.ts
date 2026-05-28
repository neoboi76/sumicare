import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';

interface ServiceItem {
    id: number;
    code: string;
    name: string;
    durationMinutes: number;
    price: number;
    category: string;
    vip: boolean;
    fixedRate: boolean;
    description: string | null;
    imageUrl: string | null;
}

@Component({
    selector: 'sumi-services',
    standalone: true,
    imports: [RouterLink, SumiRevealDirective],
    templateUrl: './services.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesComponent implements OnInit {
    private http = inject(HttpClient);
    services = signal<ServiceItem[]>([]);
    loadingInitial = signal(true);

    private readonly photoRotation: string[] = [
        'https://images.unsplash.com/photo-1600334129128-685c5582fd35?auto=format&fit=crop&w=1200&q=75',
        'https://images.unsplash.com/photo-1591343395082-e120087004b4?auto=format&fit=crop&w=1200&q=75',
        'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=75'
    ];

    private readonly descriptionByKeyword: { match: string[]; description: string }[] = [
        { match: ['swedish'], description: 'A classic full-body massage with long gliding strokes and gentle kneading. The most popular entry point and the easiest on first-timers.' },
        { match: ['shiatsu'], description: 'A Japanese tradition that uses focused finger pressure along the body\'s energy meridians to relieve tension and restore balance.' },
        { match: ['thai'], description: 'Done over loose clothing, Thai massage combines assisted stretching with rhythmic compressions for full-body mobility.' },
        { match: ['deep'], description: 'Slower, firmer pressure that reaches the deeper layers of muscle. Best for stubborn knots and chronic tightness.' },
        { match: ['lomi'], description: 'A Hawaiian flow-based massage with long, sweeping forearm strokes. Deeply relaxing and grounding.' },
        { match: ['lymphatic'], description: 'Light, rhythmic strokes that encourage lymph drainage. A gentle session focused on circulation and reducing puffiness.' },
        { match: ['combination'], description: 'A flexible blend of Swedish, deep tissue, and aromatherapy customised to your preferred pressure and focus areas.' },
        { match: ['foot', 'reflex'], description: 'Pressure-point work on the feet that maps to the rest of the body. A great choice if you spend long hours on your feet.' },
        { match: ['salt'], description: 'A revitalising sea-salt body scrub that exfoliates and softens the skin, followed by a warm rinse.' },
        { match: ['dae mi di'], description: 'The signature Korean scrub. Vigorous full-body exfoliation that leaves the skin smooth and renewed.' },
        { match: ['milk'], description: 'A nourishing milk and oat scrub that gently exfoliates and moisturises in a single treatment.' },
        { match: ['aromatherapy', 'aroma'], description: 'A relaxing massage with essential oils chosen for your mood, combined with focused reflexology on the feet.' },
        { match: ['ventosa'], description: 'Glass-cup massage that uses gentle suction to release deep muscular tension and improve circulation.' },
        { match: ['tandem'], description: 'Two therapists working in synchrony for a fast, immersive, full-body experience. Recommended after you have tried our solo menu.' }
    ];

    ngOnInit(): void {
        this.http
            .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/public/services/${environment.defaultOrganizationSlug}`)
            .subscribe({
                next: (s) => { this.services.set(s); this.loadingInitial.set(false); },
                error: () => { this.services.set([]); this.loadingInitial.set(false); }
            });
    }

    photoFor(index: number): string {
        return this.photoRotation[index % this.photoRotation.length];
    }

    descriptionFor(s: ServiceItem): string {
        if (s.description) return s.description;
        const key = s.name.toLowerCase();
        for (const row of this.descriptionByKeyword) {
            if (row.match.some(m => key.includes(m))) return row.description;
        }
        return 'A signature treatment from the Lasema menu. See the Packages page for inclusions and rates.';
    }
}
