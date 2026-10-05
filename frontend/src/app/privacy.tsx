import { PolicyPage } from '@/screens/policies/policy-page';
import { privacyPolicy } from '@/screens/policies/content';

export default function PrivacyRoute() {
  return <PolicyPage policy={privacyPolicy} />;
}
