export function homePathFor(roles: string[]) {
  return roles.includes('COURSE_ADMIN') || roles.includes('ADMIN') ? '/admin/courses' : '/courses'
}
