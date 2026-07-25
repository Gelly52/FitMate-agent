import type { Config } from 'tailwindcss'

/**
 * Pixel Art UI - Tailwind CSS Configuration
 *
 * This configuration establishes the design token layer for a pixel art
 * themed interface. All custom values follow the pixel-grid aesthetic:
 * hard shadows, limited palette, discrete sizes.
 */
const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        'pixel-black': '#101010',
        'pixel-white': '#F8F8F8',
        'pixel-gray': '#6B6B6B',
        'pixel-red': '#A83232',
        'pixel-green': '#2D7D46',
        'pixel-blue': '#3A5BA0',
        'pixel-yellow': '#D4A533',
      },
      fontFamily: {
        pixel: ['VT323', 'ZCOOL KuaiLe', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        'pixel-sm': '2px 2px 0px 0px #101010',
        'pixel': '4px 4px 0px 0px #101010',
        'pixel-md': '3px 3px 0px 0px #101010',
        'pixel-lg': '6px 6px 0px 0px #101010',
        'pixel-xl': '8px 8px 0px 0px #101010',
        'pixel-inset': 'inset 2px 2px 0px 0px #101010',
        'pixel-disabled': '2px 2px 0px 0px #666666',
      },
      animation: {
        'shake': 'shake 0.3s ease-in-out infinite',
        'bounce-pixel': 'bounce-pixel 1s ease-in-out infinite',
        'float': 'float 3s ease-in-out infinite',
        'typewriter': 'typewriter 0.5s steps(1) infinite',
      },
      keyframes: {
        'shake': {
          '0%, 100%': { transform: 'translateX(0)' },
          '25%': { transform: 'translateX(-2px)' },
          '75%': { transform: 'translateX(2px)' },
        },
        'bounce-pixel': {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-8px)' },
        },
        'float': {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-4px)' },
        },
        'typewriter': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0' },
        },
      },
    },
  },
  plugins: [
    function({ addUtilities }: { addUtilities: (utilities: Record<string, Record<string, string>>) => void }) {
      addUtilities({
        '.pixelated': {
          imageRendering: 'pixelated',
        },
      });
    },
  ],
}

export default config
