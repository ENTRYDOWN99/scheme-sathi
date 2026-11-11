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
  }
};

export default nextConfig;
