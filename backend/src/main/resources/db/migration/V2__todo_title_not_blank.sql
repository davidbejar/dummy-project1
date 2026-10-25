-- Same rule as @NotBlank in Java and the trim() check in the React form.
ALTER TABLE todo ADD CONSTRAINT todo_title_not_blank CHECK (btrim(title) <> '');
