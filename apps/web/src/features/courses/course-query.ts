import type { UseQueryResult } from '@tanstack/react-query'
import type { Course } from './course-api.ts'

/** True when the cached course row matches the route param (avoids stale UI while switching courses). */
export function courseDataMatchesRoute(
  course: UseQueryResult<Course, unknown>,
  courseId: string,
): course is UseQueryResult<Course, unknown> & { data: Course } {
  return course.data?.id === courseId
}

export function isCourseContextPending(course: UseQueryResult<Course, unknown>, courseId: string): boolean {
  if (!courseId) {
    return true
  }
  if (course.isLoading) {
    return true
  }
  if (course.isFetching && !courseDataMatchesRoute(course, courseId)) {
    return true
  }
  return course.data != null && course.data.id !== courseId
}
