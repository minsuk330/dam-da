import { PolicyPage } from '@/screens/policies/policy-page';
import { privacyConsent } from '@/screens/policies/content';

export default function PrivacyConsentRoute() {
  return <PolicyPage policy={privacyConsent} />;
}
