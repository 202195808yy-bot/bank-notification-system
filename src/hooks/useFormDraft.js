import { useEffect } from 'react';
import { Form } from 'antd';

export function useFormDraft(form, key) {
  useEffect(() => {
    const saved = localStorage.getItem(key);
    if (saved) {
      try {
        form.setFieldsValue(JSON.parse(saved));
      } catch (e) {
        // ignore
      }
    }
  }, []);

  const saveDraft = () => {
    const values = form.getFieldsValue();
    localStorage.setItem(key, JSON.stringify(values));
  };

  const clearDraft = () => {
    localStorage.removeItem(key);
  };

  return { saveDraft, clearDraft };
}