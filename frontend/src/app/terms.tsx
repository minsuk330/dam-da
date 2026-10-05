import { PolicyPage } from '@/screens/policies/policy-page';
import { termsOfService } from '@/screens/policies/content';

export default function TermsRoute() {
  return <PolicyPage policy={termsOfService} />;
}
