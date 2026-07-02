import defaultTheme from '../src/themes/theme-default-light.json';

export default {
  'GET /api/theme/:themename': (_req: any, res: any) => {
    res.json(defaultTheme);
  },
};
