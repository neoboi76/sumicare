export interface OrganizationBranding {
  id: string;
  slug: string;
  displayName: string;
  logoUrl: string | null;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  theme: string;
}

export interface UpdateBrandingRequest {
  displayName?: string;
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
  theme?: string;
}
