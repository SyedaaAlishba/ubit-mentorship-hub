import { useState, type FormEvent } from 'react';
import Button from '@/components/ui/Button';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Textarea from '@/components/ui/Textarea';
import { useAuth } from '@/context/authContext';
import { useNavigation } from '@/context/navigationContext';
import { questionsApi } from '@/services/questionsApi';
import type { QuestionCategory } from '@/types/api';
import { ArrowLeft, Tag, X, Send } from 'lucide-react';
import styles from './AskQuestionScreen.module.css';

const CATEGORY_OPTIONS: { value: QuestionCategory; label: string }[] = [
  { value: 'ACADEMIC', label: 'Academic Guidance' },
  { value: 'CAREER', label: 'Career & Internships' },
  { value: 'TECHNICAL', label: 'Technical & Projects' },
  { value: 'CAMPUS', label: 'Campus Life & Events' },
  { value: 'GENERAL', label: 'General Advice' },
];

export default function AskQuestionScreen() {
  const { navigate, goBack } = useNavigation();
  const { user } = useAuth();
  const isVerified = user?.state === 'VERIFIED' || user?.verificationStatus === 'VERIFIED';

  // Redirect or inform if user is not verified
  if (!isVerified) {
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Button
            variant="ghost"
            size="sm"
            onClick={goBack}
            leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
          >
            Back
          </Button>
          <h1 className={styles.title}>Ask a Question</h1>
          <p className={styles.subtitle}>
            You must complete verification before asking questions.
          </p>
        </div>
      </div>
    );
  }

  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [category, setCategory] = useState<QuestionCategory>('ACADEMIC');
  const [tagInput, setTagInput] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleAddTag() {
    const trimmed = tagInput.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '');
    if (trimmed && !tags.includes(trimmed) && tags.length < 5) {
      setTags([...tags, trimmed]);
      setTagInput('');
    }
  }

  function handleRemoveTag(tag: string) {
    setTags(tags.filter(t => t !== tag));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (title.trim().length < 5) {
      setError('Title must be at least 5 characters.');
      return;
    }
    if (body.trim().length < 10) {
      setError('Question body must be at least 10 characters.');
      return;
    }

    setPending(true);
    try {
      const q = await questionsApi.create({
        title: title.trim(),
        body: body.trim(),
        category,
        tags: tags.length > 0 ? tags : undefined,
      });
      navigate({ view: 'QUESTION_DETAIL', questionId: q.questionId });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Could not submit your question. Please try again.';
      setError(message);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <Button
          variant="ghost"
          size="sm"
          onClick={goBack}
          leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
        >
          Back to Questions
        </Button>
        <h1 className={styles.title}>Ask a Question</h1>
        <p className={styles.subtitle}>
          Get answers from fellow UBIT students, seniors, and alumni who have been through the same journey.
        </p>
      </div>

      <Card className={styles.formCard}>
        {error && (
          <div className={styles.errorBanner} role="alert">
            {error}
          </div>
        )}
        <form onSubmit={(e) => void handleSubmit(e)} className={styles.form}>
          <Input
            label="Title"
            placeholder="e.g. How to prepare for the 5th semester Operating Systems exam?"
            value={title}
            onChange={e => setTitle(e.target.value)}
            helperText="Be specific and concise so others understand your question immediately"
            required
          />
          <Select
            label="Category"
            value={category}
            onChange={e => setCategory(e.target.value as QuestionCategory)}
            options={CATEGORY_OPTIONS}
          />
          <Textarea
            label="Details / Description"
            placeholder="Explain what you are trying to do, what you have tried, and what guidance you are looking for..."
            value={body}
            onChange={e => setBody(e.target.value)}
            helperText="Include all relevant context"
            required
          />
          <div className={styles.tagSection}>
            <label className={styles.tagLabel}>Tags (up to 5)</label>
            <div className={styles.tagInputWrapper}>
              <Input
                placeholder="e.g. exam, python, fyp"
                value={tagInput}
                onChange={e => setTagInput(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleAddTag();
                  }
                }}
              />
              <Button type="button" variant="secondary" onClick={handleAddTag}>
                Add Tag
              </Button>
            </div>
            {tags.length > 0 && (
              <div className={styles.tags}>
                {tags.map(tag => (
                  <span key={tag} className={styles.tag}>
                    <Tag size={11} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '3px' }} />
                    {tag}
                    <button
                      type="button"
                      className={styles.removeTagBtn}
                      onClick={() => handleRemoveTag(tag)}
                      aria-label={`Remove tag ${tag}`}
                    >
                      <X size={12} strokeWidth={2} />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
          <div className={styles.footer}>
            <Button type="button" variant="ghost" onClick={goBack} disabled={pending}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={pending}
              disabled={pending}
              leftIcon={<Send size={15} strokeWidth={2} />}
            >
              Publish Question
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
