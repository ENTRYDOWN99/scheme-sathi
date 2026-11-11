import path from 'node:path';
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',
  experimental: {
    externalDir: true
  },
  webpack: (config) => {
    config.resolve.alias['@shared'] = path.resolve(process.cwd(), '../shared');
    return config;
  },
  async rewrites() {
    const backendUrl = (process.env.BACKEND_URL || 'http://localhost:4000').replace(/\/$/, '');
    return [
      {
        source: '/api/v1/:path*',
        destination: `${backendUrl}/api/v1/:path*`
      }
    ];
  }
};

export default nextConfig;
