import { BrowserRouter, Route, Routes } from 'react-router'
import { AdminCoursesPage } from '../features/admin-courses/AdminCoursesPage.tsx'
import { AdminMaterialsPage } from '../features/admin-courses/AdminMaterialsPage.tsx'
import { LoginPage } from '../features/auth/LoginPage.tsx'
import { RegisterPage } from '../features/auth/RegisterPage.tsx'
import { CourseFlashcardsPage } from '../features/courses/CourseFlashcardsPage.tsx'
import { CourseHomePage } from '../features/courses/CourseHomePage.tsx'
import { CourseQaPage } from '../features/courses/CourseQaPage.tsx'
import { CourseQuizPage } from '../features/courses/CourseQuizPage.tsx'
import { CoursesPage } from '../features/courses/CoursesPage.tsx'
import { ProfilePage } from '../features/profile/ProfilePage.tsx'
import { AppShell } from './AppShell.tsx'
import { HomePage } from './HomePage.tsx'
import { RequireAuth, RequireRole } from './guards.tsx'

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/courses"
            element={
              <RequireAuth>
                <CoursesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/courses/:courseId"
            element={
              <RequireAuth>
                <CourseHomePage />
              </RequireAuth>
            }
          />
          <Route
            path="/courses/:courseId/qa"
            element={
              <RequireAuth>
                <CourseQaPage />
              </RequireAuth>
            }
          />
          <Route
            path="/courses/:courseId/flashcards"
            element={
              <RequireAuth>
                <CourseFlashcardsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/courses/:courseId/quiz"
            element={
              <RequireAuth>
                <CourseQuizPage />
              </RequireAuth>
            }
          />
          <Route
            path="/admin/courses"
            element={
              <RequireRole role="COURSE_ADMIN">
                <AdminCoursesPage />
              </RequireRole>
            }
          />
          <Route
            path="/admin/courses/:courseId/materials"
            element={
              <RequireRole role="COURSE_ADMIN">
                <AdminMaterialsPage />
              </RequireRole>
            }
          />
          <Route
            path="/profile"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
