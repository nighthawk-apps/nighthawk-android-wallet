#!/usr/bin/env bash

# audit_ai_instructions.sh
# Audits the project for violations of AI_INSTRUCTIONS.md

OUTPUT_FILE="$1"

if [ -z "$OUTPUT_FILE" ]; then
    echo "Please provide an output file."
    exit 1
fi

echo "# AI Instructions Audit Report" > "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

TOTAL_KT_FILES=$(find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" | wc -l | tr -d ' ')
echo "Total Kotlin files analyzed: **$TOTAL_KT_FILES**" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Rule 3: String concatenation
STRING_CONCAT_COUNT=$(find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" -exec grep -HnE '\+ "' {} + | wc -l | tr -d ' ')
echo "## Rule 3: String Templates" >> "$OUTPUT_FILE"
echo "Found **$STRING_CONCAT_COUNT** potential string concatenations (e.g. \`+ \"\`)." >> "$OUTPUT_FILE"
if [ "$STRING_CONCAT_COUNT" -gt 0 ]; then
    echo "\`\`\`" >> "$OUTPUT_FILE"
    find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" -exec grep -HnE '\+ "' {} + | head -n 10 >> "$OUTPUT_FILE"
    echo "... (showing top 10)" >> "$OUTPUT_FILE"
    echo "\`\`\`" >> "$OUTPUT_FILE"
fi
echo "" >> "$OUTPUT_FILE"

# Rule 6: Nullability
NULL_CHECK_COUNT=$(find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" -exec grep -HnE '!= null|== null' {} + | wc -l | tr -d ' ')
echo "## Rule 6: Nullability (Safe Calls)" >> "$OUTPUT_FILE"
echo "Found **$NULL_CHECK_COUNT** explicit null checks (\`!= null\` or \`== null\`)." >> "$OUTPUT_FILE"
if [ "$NULL_CHECK_COUNT" -gt 0 ]; then
    echo "\`\`\`" >> "$OUTPUT_FILE"
    find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" -exec grep -HnE '!= null|== null' {} + | head -n 10 >> "$OUTPUT_FILE"
    echo "... (showing top 10)" >> "$OUTPUT_FILE"
    echo "\`\`\`" >> "$OUTPUT_FILE"
fi
echo "" >> "$OUTPUT_FILE"

# Rule 8: Immutability
VAR_COUNT=$(find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" -exec grep -Hnw 'var' {} + | wc -l | tr -d ' ')
echo "## Rule 8: Immutability (var vs val)" >> "$OUTPUT_FILE"
echo "Found **$VAR_COUNT** usages of \`var\` declarations." >> "$OUTPUT_FILE"
if [ "$VAR_COUNT" -gt 0 ]; then
    echo "\`\`\`" >> "$OUTPUT_FILE"
    find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/.idea/*" -exec grep -Hnw 'var' {} + | head -n 10 >> "$OUTPUT_FILE"
    echo "... (showing top 10)" >> "$OUTPUT_FILE"
    echo "\`\`\`" >> "$OUTPUT_FILE"
fi
echo "" >> "$OUTPUT_FILE"

echo "Audit completed successfully."
