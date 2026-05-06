const { join } = require('path');

module.exports = {
  content: [
    join(__dirname, 'src/**/*.{html,ts}'),
    join(__dirname, '../../libs/ui/src/**/*.{html,ts}')
  ],
  theme: {
    extend: {
      colors: {
        primary: 'var(--sumi-primary)',
        secondary: 'var(--sumi-secondary)',
        accent: 'var(--sumi-accent)'
      }
    }
  },
  plugins: []
};
