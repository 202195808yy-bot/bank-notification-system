import { create } from 'zustand';
import axios from '../api/axiosInstance';

const useTemplateStore = create((set, get) => ({
  templates: [],
  loading: false,

  // params 三个维度都可选：后端 TemplateService.findAll 是动态查询，只传一个也能筛
  fetchTemplates: async (params = {}) => {
    set({ loading: true });
    const query = Object.fromEntries(Object.entries(params).filter(([, v]) => v));
    try {
      const { data } = await axios.get('/templates', { params: query });
      set({
        templates: Array.isArray(data) ? data : data.content || [],
        loading: false,
      });
    } catch (e) {
      set({ loading: false });
    }
  },

  addTemplate: async (values) => {
    await axios.post('/templates', values);
    get().fetchTemplates();
  },

  editTemplate: async (id, values) => {
    await axios.put(`/templates/${id}`, values);
    get().fetchTemplates();
  },

  removeTemplate: async (id) => {
    await axios.delete(`/templates/${id}`);
    get().fetchTemplates();
  },
}));

export default useTemplateStore;