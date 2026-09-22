import { create } from 'zustand';
import { getTemplates, createTemplate, updateTemplate, deleteTemplate } from '../api/templateApi';

const useTemplateStore = create((set, get) => ({
  templates: [],
  loading: false,

  // params 三个维度都可选：后端 TemplateService.findAll 是动态查询，只传一个也能筛
  fetchTemplates: async (params = {}) => {
    set({ loading: true });
    const query = Object.fromEntries(Object.entries(params).filter(([, v]) => v));
    try {
      // 解包（数组 / content / data 三种返回形态）只在 templateApi 里做一次，
      // 这里不再重复一遍，否则两处口径会分叉
      set({ templates: await getTemplates(query), loading: false });
    } catch (e) {
      set({ loading: false });
    }
  },

  addTemplate: async (values) => {
    await createTemplate(values);
    get().fetchTemplates();
  },

  editTemplate: async (id, values) => {
    await updateTemplate(id, values);
    get().fetchTemplates();
  },

  removeTemplate: async (id) => {
    await deleteTemplate(id);
    get().fetchTemplates();
  },
}));

export default useTemplateStore;