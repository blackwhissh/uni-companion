export function homePathFor(roles: string[]) {
  if (roles.includes('ADMIN')) {
    return '/admin'
  }
  if (roles.includes('COURSE_ADMIN')) {
    return '/admin/courses'
  }
  return '/courses'
}
