# Explorer Fix 3 Context: Downstream Contract Alignment for Milestone 1.1-B
Audit IsbnValidator and MetadataService contracts against the CatalogCacheDao fix:
- Ensure IsbnValidator.clean() and CatalogCacheDao cleaning logic are 100% harmonious
- Ensure MetadataService caching in L1 (ConcurrentHashMap) and L2 (CatalogCacheDao) handles uppercase ISBN-10 check digits uniformly.
