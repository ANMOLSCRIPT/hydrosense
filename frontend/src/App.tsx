import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { CitizenLayout, DashboardLayout } from './components/Layout'
import { EmptyState, Skeleton } from './components/ui'
import { ModeProvider } from './lib/mode'
import { ToastProvider } from './lib/toast'
import About from './pages/About'
import Alerts from './pages/Alerts'
import Home from './pages/Home'
import Report from './pages/Report'

// The map and the dashboard are loaded on demand to keep the first page light.
const Explore = lazy(() => import('./pages/Explore'))
const SiteDetail = lazy(() => import('./pages/SiteDetail'))
const Overview = lazy(() => import('./pages/dashboard/Overview'))
const Sites = lazy(() => import('./pages/dashboard/Sites'))
const SiteAnalytics = lazy(() => import('./pages/dashboard/SiteAnalytics'))
const Insights = lazy(() => import('./pages/dashboard/Insights'))
const Devices = lazy(() => import('./pages/dashboard/Devices'))
const DeviceTest = lazy(() => import('./pages/dashboard/DeviceTest'))
const AlertsAdmin = lazy(() => import('./pages/dashboard/AlertsAdmin'))

const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 4000, retry: 1, refetchOnWindowFocus: true } } })

function NotFound() {
  return (
    <div className="container-page max-w-xl py-20">
      <EmptyState title="We couldn't find that page" action={<Link to="/" className="btn-primary">Back to home</Link>}>The link may be old, or the page may have moved.</EmptyState>
    </div>
  )
}
const Loading = () => <div className="container-page py-10"><Skeleton className="h-64 w-full" /></div>

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ModeProvider>
        <ToastProvider>
          <BrowserRouter>
            <Suspense fallback={<Loading />}>
              <Routes>
                <Route element={<CitizenLayout />}>
                  <Route index element={<Home />} />
                  <Route path="explore" element={<Explore />} />
                  <Route path="explore/:siteId" element={<SiteDetail />} />
                  <Route path="report" element={<Report />} />
                  <Route path="alerts" element={<Alerts />} />
                  <Route path="about" element={<About />} />
                  <Route path="*" element={<NotFound />} />
                </Route>
                <Route path="dashboard" element={<DashboardLayout />}>
                  <Route index element={<Overview />} />
                  <Route path="sites" element={<Sites />} />
                  <Route path="sites/:id" element={<SiteAnalytics />} />
                  <Route path="analytics" element={<SiteAnalytics />} />
                  <Route path="insights" element={<Insights />} />
                  <Route path="devices" element={<Devices />} />
                  <Route path="devices/test" element={<DeviceTest />} />
                  <Route path="alerts" element={<AlertsAdmin />} />
                  <Route path="*" element={<NotFound />} />
                </Route>
              </Routes>
            </Suspense>
          </BrowserRouter>
        </ToastProvider>
      </ModeProvider>
    </QueryClientProvider>
  )
}
