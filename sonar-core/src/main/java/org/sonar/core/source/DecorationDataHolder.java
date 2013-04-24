/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.core.source;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

class DecorationDataHolder {

  private static final String ENTITY_SEPARATOR = ";";
  private static final String FIELD_SEPARATOR = ",";
  private static final String SYMBOL_PREFIX = "sym-";
  private static final String HIGHLIGHTABLE = "sym";

  private List<TagEntry> openingTagsEntries;
  private int openingTagsIndex;
  private List<Integer> closingTagsOffsets;
  private int closingTagsIndex;

  DecorationDataHolder() {
    openingTagsEntries = Lists.newArrayList();
    closingTagsOffsets = Lists.newArrayList();
  }

  void loadSymbolReferences(String symbolsReferences) {
    String[] symbols = symbolsReferences.split(ENTITY_SEPARATOR);
    for (String symbol : symbols) {
      String[] symbolFields = symbol.split(FIELD_SEPARATOR);
      int declarationStartOffset = Integer.parseInt(symbolFields[0]);
      int declarationEndOffset = Integer.parseInt(symbolFields[1]);
      int symbolLength = declarationEndOffset - declarationStartOffset;
      String[] symbolOccurrences = Arrays.copyOfRange(symbolFields, 2, symbolFields.length);
      loadSymbolOccurrences(declarationStartOffset, symbolLength, symbolOccurrences);
    }
  }

  void loadSyntaxHighlightingData(String syntaxHighlightingRules) {
    String[] rules = syntaxHighlightingRules.split(ENTITY_SEPARATOR);
    for (String rule : rules) {
      String[] ruleFields = rule.split(FIELD_SEPARATOR);
      insertAndPreserveOrder(new TagEntry(Integer.parseInt(ruleFields[0]), ruleFields[2]), openingTagsEntries);
      insertAndPreserveOrder(Integer.parseInt(ruleFields[1]), closingTagsOffsets);
    }
  }

  List<TagEntry> getOpeningTagsEntries() {
    return openingTagsEntries;
  }

  TagEntry getCurrentOpeningTagEntry() {
    return openingTagsIndex < openingTagsEntries.size() ? openingTagsEntries.get(openingTagsIndex) : null;
  }

  void nextOpeningTagEntry() {
    openingTagsIndex++;
  }

  List<Integer> getClosingTagsOffsets() {
    return closingTagsOffsets;
  }

  int getCurrentClosingTagOffset() {
    return closingTagsIndex < closingTagsOffsets.size() ? closingTagsOffsets.get(closingTagsIndex) : -1;
  }

  void nextClosingTagOffset() {
    closingTagsIndex++;
  }

  private void loadSymbolOccurrences(int declarationStartOffset, int symbolLength, String[] symbolOccurrences) {
    for (int i = 0; i < symbolOccurrences.length; i++) {
      int occurrenceStartOffset = Integer.parseInt(symbolOccurrences[i]);
      int occurrenceEndOffset = occurrenceStartOffset + symbolLength;
      insertAndPreserveOrder(new TagEntry(occurrenceStartOffset, SYMBOL_PREFIX + declarationStartOffset + " " + HIGHLIGHTABLE), openingTagsEntries);
      insertAndPreserveOrder(occurrenceEndOffset, closingTagsOffsets);
    }
  }

  private void insertAndPreserveOrder(TagEntry newEntry, List<TagEntry> orderedEntries) {
    int insertionIndex = 0;
    Iterator<TagEntry> entriesIterator = orderedEntries.iterator();
    while (entriesIterator.hasNext() && entriesIterator.next().getStartOffset() <= newEntry.getStartOffset()) {
      insertionIndex++;
    }
    orderedEntries.add(insertionIndex, newEntry);
  }

  private void insertAndPreserveOrder(int newOffset, List<Integer> orderedOffsets) {
    int insertionIndex = 0;
    Iterator<Integer> entriesIterator = orderedOffsets.iterator();
    while (entriesIterator.hasNext() && entriesIterator.next() <= newOffset) {
      insertionIndex++;
    }
    orderedOffsets.add(insertionIndex, newOffset);
  }
}
