/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';

interface MassageRate {
    name: string;
    weekdayRegular: number;
    weekdayPromo: number;
    weekendRegular: number;
    weekendPromo: number;
}

interface PackageHighlight {
    title: string;
    korean: string;
    summary: string;
    inclusions: string[];
    rateLines: string[];
}

@Component({
    selector: 'sumi-public-packages',
    standalone: true,
    imports: [RouterLink, DecimalPipe, SumiRevealDirective],
    templateUrl: './packages.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class PackagesComponent {
    readonly highlights: PackageHighlight[] = [
        {
            title: 'Lasema Full Package',
            korean: '풀패키지',
            summary: '12-hour full-facility access with a one-hour full body massage and unlimited kiln-room time.',
            inclusions: [
                'Full use of the jacuzzi, towel, and locker',
                'Steam sauna, hot & cold shower',
                '60-minute full body massage',
                'Access to every kiln (Ice, Amethyst, Himalayan Salt, Ochre Hot, Pine Tree)',
                'Sleeping cave, gym, entertainment area, K-drama and movies, Korean photobooth',
                '12 hours maximum stay'
            ],
            rateLines: [
                'Mon-Thu: ₱2,000 regular / ₱1,750 promo',
                'Fri-Sun & Holidays: ₱2,200 regular / ₱1,950 promo'
            ]
        },
        {
            title: 'Lasema Couple Package',
            korean: '커플 풀패키지',
            summary: 'Private couple\'s room for two with a one-hour massage each, dessert wine, and full kiln access.',
            inclusions: [
                'Private couple\'s room',
                '60-minute massage and 60-minute private jacuzzi each',
                'Two glasses of wine with dessert',
                'Two guest kits',
                'Full use of the jacuzzi, towel, and locker',
                'Steam sauna, hot & cold shower',
                'Access to every kiln, sleeping cave, gym, entertainment area, photobooth',
                '12 hours maximum stay'
            ],
            rateLines: [
                'Per couple: ₱7,500 regular / ₱6,800 promo'
            ]
        },
        {
            title: 'Massage Couple Package',
            korean: '마사지 커플 패키지',
            summary: 'Private couple\'s room with the essentials of the couple flow, capped at 3 hours.',
            inclusions: [
                'Private couple\'s room',
                '60-minute massage and 60-minute private jacuzzi each',
                'Two glasses of wine with dessert',
                'Two guest kits',
                'Full use of the jacuzzi, towel, and locker',
                'Steam sauna, hot & cold shower',
                '3 hours maximum stay'
            ],
            rateLines: [
                'Per couple: ₱5,500 regular / ₱5,000 promo'
            ]
        },
        {
            title: 'Sauna Package',
            korean: '사우나',
            summary: 'Essential bathhouse experience for a relaxing reset.',
            inclusions: [
                'Full use of the jacuzzi, towel, and locker',
                'Steam sauna, hot & cold shower',
                '3 hours maximum stay'
            ],
            rateLines: [
                'Mon-Thu: ₱700 regular / ₱650 promo',
                'Fri-Sun & Holidays: ₱700 regular / ₱650 promo'
            ]
        },
        {
            title: 'Massage Package',
            korean: '마사지',
            summary: 'A 60-minute massage paired with the sauna and shower facilities.',
            inclusions: [
                'Full use of the jacuzzi, towel, and locker',
                'Steam sauna, hot & cold shower',
                '60-minute full body massage',
                '3 hours maximum stay'
            ],
            rateLines: [
                'Mon-Thu: ₱1,600 regular / ₱1,450 promo',
                'Fri-Sun & Holidays: ₱1,750 regular / ₱1,550 promo'
            ]
        },
        {
            title: 'Jjimjilbang Package',
            korean: '찜질방',
            summary: 'All-day kiln-room access for the full traditional jjimjilbang experience.',
            inclusions: [
                'Full use of the jacuzzi, towel, and locker',
                'Steam sauna, hot & cold shower',
                'Every kiln: Ice, Amethyst, Himalayan Salt, Ochre Hot, Pine Tree',
                'Sleeping cave, gym, entertainment area, K-drama, photobooth',
                '12 hours maximum stay'
            ],
            rateLines: [
                'Mon-Thu: ₱1,300 regular / ₱1,050 promo',
                'Fri-Sun & Holidays: ₱1,500 regular / ₱1,150 promo'
            ]
        }
    ];

    readonly fullPackageRates: MassageRate[] = [
        { name: 'Foot Reflexology', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Shiatsu', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Swedish', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Deep Tissue', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Lomi-lomi', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Lymphatic', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Combination', weekdayRegular: 2000, weekdayPromo: 1750, weekendRegular: 2200, weekendPromo: 1950 },
        { name: 'Salt Glow Scrub', weekdayRegular: 2100, weekdayPromo: 1850, weekendRegular: 2300, weekendPromo: 2050 },
        { name: 'Dae Mi Di (Korean Scrub)', weekdayRegular: 2250, weekdayPromo: 1950, weekendRegular: 2500, weekendPromo: 2200 },
        { name: 'Thai Massage', weekdayRegular: 2250, weekdayPromo: 1950, weekendRegular: 2500, weekendPromo: 2200 },
        { name: 'Milk Bath Scrub', weekdayRegular: 2300, weekdayPromo: 2000, weekendRegular: 2550, weekendPromo: 2250 },
        { name: 'Aromatherapy w/ Reflex', weekdayRegular: 2450, weekdayPromo: 2150, weekendRegular: 2750, weekendPromo: 2400 },
        { name: 'Ventosa Massage', weekdayRegular: 2750, weekdayPromo: 2450, weekendRegular: 3050, weekendPromo: 2700 },
        { name: 'Tandem Massage', weekdayRegular: 3000, weekdayPromo: 2700, weekendRegular: 3300, weekendPromo: 2950 }
    ];

    readonly massagePackageRates: MassageRate[] = [
        { name: 'Foot Reflexology', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Shiatsu', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Swedish', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Deep Tissue', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Lomi-lomi', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Lymphatic', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Combination', weekdayRegular: 1600, weekdayPromo: 1450, weekendRegular: 1750, weekendPromo: 1550 },
        { name: 'Salt Glow Scrub', weekdayRegular: 1700, weekdayPromo: 1550, weekendRegular: 1850, weekendPromo: 1650 },
        { name: 'Dae Mi Di (Korean Scrub)', weekdayRegular: 1850, weekdayPromo: 1650, weekendRegular: 2050, weekendPromo: 1800 },
        { name: 'Thai Massage', weekdayRegular: 1850, weekdayPromo: 1650, weekendRegular: 2050, weekendPromo: 1800 },
        { name: 'Milk Bath Scrub', weekdayRegular: 1900, weekdayPromo: 1700, weekendRegular: 2100, weekendPromo: 1850 },
        { name: 'Aromatherapy w/ Reflex', weekdayRegular: 2050, weekdayPromo: 1850, weekendRegular: 2300, weekendPromo: 2000 },
        { name: 'Ventosa Massage', weekdayRegular: 2350, weekdayPromo: 2150, weekendRegular: 2600, weekendPromo: 2300 },
        { name: 'Tandem Massage', weekdayRegular: 2600, weekdayPromo: 2400, weekendRegular: 2850, weekendPromo: 2550 }
    ];

    readonly extras = [
        { label: 'Overtime', value: '₱250 per hour' },
        { label: 'Private Karaoke', value: '₱800 per hour' },
        { label: 'Extra Uniform', value: '₱100 per uniform' },
        { label: 'Large Towel Rental', value: '₱100 per piece' },
        { label: 'Regular Private Room for Massage', value: '₱500' },
        { label: 'Sitzbath', value: '₱500 per hour' },
        { label: 'Child Rate (Jjimjilbang, 1-5 yrs old)', value: '₱780 weekday / ₱880 weekend' }
    ];
}
