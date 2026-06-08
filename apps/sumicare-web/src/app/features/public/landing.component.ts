import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';
import { SumiParallaxDirective } from '../../shared/directives/sumi-parallax.directive';
import { SumiCounterDirective } from '../../shared/directives/sumi-counter.directive';

interface MosaicTile {
    src: string;
    alt: string;
    label: string;
    caption: string;
}

@Component({
    selector: 'sumi-landing',
    standalone: true,
    imports: [RouterLink, SumiRevealDirective, SumiParallaxDirective, SumiCounterDirective],
    templateUrl: './landing.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingComponent {
    readonly heroImage = 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=2400&q=80';

    readonly mosaicLarge: MosaicTile = {
        src: 'https://images.unsplash.com/photo-1600334129128-685c5582fd35?auto=format&fit=crop&w=1600&q=75',
        alt: 'Hot-stone signature massage',
        label: 'Signature Massage',
        caption: 'Hot-stone and herbal aromatherapy'
    };

    readonly mosaicSmall: MosaicTile[] = [
        {
            src: 'https://images.unsplash.com/photo-1571902943202-507ec2618e8f?auto=format&fit=crop&w=900&q=75',
            alt: 'Studio interior',
            label: 'The Studio',
            caption: 'Where every session begins'
        },
        {
            src: 'https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=900&q=75',
            alt: 'Oils and balms',
            label: "Therapist's Touch",
            caption: 'Oils, balms, and breath work'
        },
        {
            src: 'https://images.unsplash.com/photo-1564540583246-934409427776?auto=format&fit=crop&w=900&q=75',
            alt: 'Private bath wing',
            label: 'Marble & Light',
            caption: 'The private bath wing'
        },
        {
            src: 'https://images.unsplash.com/photo-1583416750470-965b2707b355?auto=format&fit=crop&w=900&q=75',
            alt: 'Cedar sauna interior',
            label: 'Cedar & Heat',
            caption: 'One of seven sauna rooms'
        }
    ];

    readonly stripImages: MosaicTile[] = [
        {
            src: '/assets/lasema/Lasema-package-1.jpg',
            alt: 'Lasema 12-hour package menu',
            label: 'Lasema Package',
            caption: 'The 12-hour stay menu'
        },
        {
            src: '/assets/lasema/Lasema-package-2.jpg',
            alt: 'Lasema 3-hour massage package menu',
            label: 'Massage Package',
            caption: 'The 3-hour menu'
        },
        {
            src: 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=75',
            alt: 'Lasema interior',
            label: 'San Antonio Village',
            caption: 'Sampaloc Street &middot; Makati'
        }
    ];
}
