/**
 * Main application router and state-driven view switch.
 *
 * Auth state drives the outer screen:
 * - NOT_AUTHENTICATED -> SignInScreen
 * - ONBOARDING_REQUIRED -> OnboardingScreen
 * - PENDING_VERIFICATION / REJECTED -> VerificationScreen
 * - VERIFIED -> Authenticated AppShell with NavigationProvider
 *
 * For administrators with ADMIN role, access to the AppShell is also granted
 * so they can access the Admin Console and verify users.
 */

import AppHeader from '@/components/AppHeader'
import AppShell from '@/components/layout/AppShell'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { NavigationProvider } from '@/context/NavigationProvider'
import AdminScreen from '@/features/admin/AdminScreen'
import AlumniDirectoryScreen from '@/features/alumni/AlumniDirectoryScreen'
import OnboardingScreen from '@/features/auth/OnboardingScreen'
import SignInScreen from '@/features/auth/SignInScreen'
import DashboardScreen from '@/features/dashboard/DashboardScreen'
import MentorshipScreen from '@/features/mentorship/MentorshipScreen'
import EditProfileScreen from '@/features/profile/EditProfileScreen'
import MyProfileScreen from '@/features/profile/MyProfileScreen'
import PublicProfileScreen from '@/features/profile/PublicProfileScreen'
import AskQuestionScreen from '@/features/questions/AskQuestionScreen'
import QuestionDetailScreen from '@/features/questions/QuestionDetailScreen'
import QuestionsScreen from '@/features/questions/QuestionsScreen'
import VerificationScreen from '@/features/verification/VerificationScreen'

export default function App() {
  const { user } = useAuth()

  if (!user) {
    return (
      <main className="page" style={{ alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
        <p className="muted">Loading UBIT Mentorship Hub...</p>
      </main>
    )
  }

  if (user.state === 'NOT_AUTHENTICATED') {
    return <SignInScreen />
  }

  if (user.state === 'ONBOARDING_REQUIRED') {
    return (
      <div className="app-shell">
        <AppHeader />
        <OnboardingScreen />
      </div>
    )
  }

  if (user.state === 'VERIFICATION_REQUIRED') {
    return <VerificationScreen />
  }

  // Onboarded users (Verified, Pending Verification, or Rejected) can enter the AppShell
  const canAccessHub =
    user.state === 'VERIFIED' ||
    user.state === 'PENDING_VERIFICATION' ||
    user.state === 'REJECTED' ||
    user.role === 'ADMIN'

  if (canAccessHub) {
    return (
      <NavigationProvider>
        <AppShell>
          <AuthenticatedContent />
        </AppShell>
      </NavigationProvider>
    )
  }

  // Fallback for any other state
  return <SignInScreen />
}

function AuthenticatedContent() {
  const { current } = useNavigation()

  switch (current.view) {
    case 'DASHBOARD':
      return <DashboardScreen />

    case 'QUESTIONS':
      return <QuestionsScreen />

    case 'QUESTION_DETAIL':
      return <QuestionDetailScreen questionId={current.questionId} />

    case 'ASK_QUESTION':
      return <AskQuestionScreen />

    case 'ALUMNI_DIRECTORY':
      return <AlumniDirectoryScreen />

    case 'PUBLIC_PROFILE':
      return <PublicProfileScreen userId={current.userId} />

    case 'MY_PROFILE':
      return <MyProfileScreen />

    case 'EDIT_PROFILE':
      return <EditProfileScreen />

    case 'MENTORSHIP':
      return <MentorshipScreen />

    case 'VERIFICATION':
      return <VerificationScreen />

    case 'ADMIN':
      return <AdminScreen />

    default:
      return <DashboardScreen />
  }
}
