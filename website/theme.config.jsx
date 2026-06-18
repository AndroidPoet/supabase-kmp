import { useConfig } from 'nextra-theme-docs'
import { useRouter } from 'next/router'

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
    const { asPath } = useRouter()
    const pageTitle = frontMatter?.title
    const title = pageTitle ? `${pageTitle} – Supabase KMP` : 'Supabase KMP'
    const description =
      frontMatter?.description ??
      'Supabase KMP — a fully typed Kotlin Multiplatform client for Supabase Auth, Database, Storage, Realtime and Edge Functions.'
    const base = 'https://androidpoet.github.io/supabase-kmp'
    const path = asPath === '/' ? '' : asPath.split('?')[0].split('#')[0]
    const canonical = `${base}${path}`
    const ogImage = `${base}/og.png`
    return (
      <>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>{title}</title>
        <meta name="description" content={description} />
        <link rel="canonical" href={canonical} />
        <link rel="icon" href={`${base}/favicon.svg`} type="image/svg+xml" />
        <meta name="theme-color" content="#3ECF8E" />
        <meta property="og:type" content="website" />
        <meta property="og:site_name" content="Supabase KMP" />
        <meta property="og:url" content={canonical} />
        <meta property="og:title" content={pageTitle ?? 'Supabase KMP'} />
        <meta property="og:description" content={description} />
        <meta property="og:image" content={ogImage} />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={pageTitle ?? 'Supabase KMP'} />
        <meta name="twitter:description" content={description} />
        <meta name="twitter:image" content={ogImage} />
      </>
    )
  },
  banner: {
    key: 'native-signin',
    content: (
      <span>
        🎉 Native Google &amp; Apple sign-in is here →{' '}
        <a href="/auth/native-sign-in" style={{ textDecoration: 'underline' }}>
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
