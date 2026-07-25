# Team Git Workflow

## 1. Sync with main

```powershell
git checkout main
git pull origin main
```

## 2. Create a branch

```powershell
git checkout -b feature/<task-name>
```

## 3. Work and commit

```powershell
git add .
git commit -m "feat(scope): short message"
```

## 4. Push branch

```powershell
git push -u origin feature/<task-name>
```

## 5. Open PR

Create a Pull Request to `main`, request review, address comments, then merge.

## 6. After merge

```powershell
git checkout main
git pull origin main
git branch -d feature/<task-name>
```

## Notes

- Avoid direct commits on `main`.
- Rebase or merge `main` into your branch frequently.
- Keep branch lifetime short to reduce conflicts.

