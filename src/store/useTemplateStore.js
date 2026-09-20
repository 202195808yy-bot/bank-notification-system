import { create } from 'zustand';
import axios from '../api/axiosInstance';

const useTemplateStore = create((set, get) => ({
  templates: [],
  loading: false,

  fetchTemplates: async () => {
    set({ loading: true });
    try {
      const { data } = await axios.get('/templates');
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