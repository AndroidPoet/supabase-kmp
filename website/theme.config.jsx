import { useConfig } from 'nextra-theme-docs'

const Logo = () => (
  <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700 }}>
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M13.2 1.6 3.3 13.4c-.7.8-.1 2 .9 2h6.4l-1 7.4c-.1.9 1 1.4 1.6.7l9.9-11.8c.7-.8.1-2-.9-2h-6.4l1-7.4c.1-.9-1-1.4-1.6-.7Z"
        fill="#3ECF8E"
      />
    </svg>
    <span>Supabase KMP</span>
  </span>
)

export default {
  logo: <Logo />,
  project: {
    link: 'https://github.com/AndroidPoet/supabase-kmp',
  },
  docsRepositoryBase: 'https://github.com/AndroidPoet/supabase-kmp/tree/main/website',
  color: {
    hue: 152,
    saturation: 60,
  },
  footer: {
    content: (
      <span>
        MIT © {new Date().getFullYear()}{' '}
        <a href="https://github.com/AndroidPoet/supabase-kmp" target="_blank" rel="noreferrer">
          Supabase KMP
        </a>
        . A Kotlin Multiplatform client for Supabase.
      </span>
    ),
  },
  head: function useHead() {
    const { frontMatter } = useConfig()
    const pageTitle = frontMatter?.title
    const title = pageTitle ? `${pageTitle} – Supabase KMP` : 'Supabase KMP'
    const description =
      frontMatter?.description ??
      'Supabase KMP — a fully typed Kotlin Multiplatform client for Supabase Auth, Database, Storage, Realtime and Edge Functions.'
    return (
      <>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>{title}</title>
        <meta name="description" content={description} />
        <meta property="og:title" content={pageTitle ?? 'Supabase KMP'} />
        <meta property="og:description" content={description} />
      </>
    )
  },
  banner: {
    key: 'native-signin',
    content: (
      <span>
        🎉 Native Google &amp; Apple sign-in is here →{' '}
        <a href="/supabase-kmp/auth/native-sign-in" style={{ textDecoration: 'underline' }}>
          read the guide
        </a>
      </span>
    ),
  },
  sidebar: {
    defaultMenuCollapseLevel: 1,
  },
  toc: {
    backToTop: true,
  },
  navigation: {
    prev: true,
    next: true,
  },
  darkMode: true,
}
