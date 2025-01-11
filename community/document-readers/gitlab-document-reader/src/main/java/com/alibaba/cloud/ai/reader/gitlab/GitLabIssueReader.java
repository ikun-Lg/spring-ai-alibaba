/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.reader.gitlab;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.IssuesApi;
import org.gitlab4j.api.models.Issue;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * GitLab issues reader.
 * Reads issues from GitLab projects or groups and converts them to documents.
 *
 * @author brianxiadong
 */
public class GitLabIssueReader extends AbstractGitLabReader {

    private final Integer groupId;

    /**
     * Constructor for GitLabIssueReader.
     *
     * @param gitLabApi GitLab API client
     * @param projectId Project ID (optional)
     * @param groupId Group ID (optional)
     * @param verbose Whether to enable verbose logging
     */
    public GitLabIssueReader(GitLabApi gitLabApi, Integer projectId, Integer groupId, boolean verbose) {
        super(gitLabApi, projectId, verbose);
        this.groupId = groupId;
    }

    /**
     * Convert a GitLab issue to a Document.
     *
     * @param issue GitLab issue
     * @return Document representation of the issue
     */
    private Document buildDocumentFromIssue(Issue issue) {
        String title = issue.getTitle();
        String description = issue.getDescription();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("state", issue.getState());
        metadata.put("labels", issue.getLabels());
        metadata.put("created_at", issue.getCreatedAt());
        metadata.put("closed_at", issue.getClosedAt());
        metadata.put("url", issue.getWebUrl());
        metadata.put("source", issue.getWebUrl());

        if (issue.getAssignee() != null) {
            metadata.put("assignee", issue.getAssignee().getUsername());
        }
        if (issue.getAuthor() != null) {
            metadata.put("author", issue.getAuthor().getUsername());
        }

        return new Document(String.valueOf(issue.getIid()), 
                          String.format("%s\n%s", title, description),
                          metadata);
    }

    /**
     * Convert LocalDateTime to ISO string format for GitLab API.
     *
     * @param dateTime LocalDateTime to convert
     * @return ISO formatted string or null
     */
    private Date toGitLabDateFormat(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public List<Document> get() {
        return loadData(null, null, null, null, null, null,
                null, null, null, null, null, null,
                GitLabIssueState.OPEN, null, null);
    }

    /**
     * Load issues from GitLab and convert them to documents.
     *
     * @param assignee Username or ID of assigned user
     * @param author Username or ID of author
     * @param confidential Filter confidential issues
     * @param createdAfter Filter issues created after date
     * @param createdBefore Filter issues created before date
     * @param iids List of issue IIDs
     * @param issueType Filter by issue type
     * @param labels List of label names
     * @param milestone Milestone title
     * @param nonArchived Return issues from non-archived projects
     * @param scope Return issues for given scope
     * @param search Search in title and description
     * @param state State of issues to retrieve
     * @param updatedAfter Filter issues updated after date
     * @param updatedBefore Filter issues updated before date
     * @return List of documents
     */
    public List<Document> loadData(String assignee,
                                 String author,
                                 Boolean confidential,
                                 LocalDateTime createdAfter,
                                 LocalDateTime createdBefore,
                                 List<Integer> iids,
                                 GitLabIssueType issueType,
                                 List<String> labels,
                                 String milestone,
                                 Boolean nonArchived,
                                 GitLabScope scope,
                                 String search,
                                 GitLabIssueState state,
                                 LocalDateTime updatedAfter,
                                 LocalDateTime updatedBefore) {
        try {
            IssuesApi issuesApi = gitLabApi.getIssuesApi();

            // Build the filter parameters
            if (state == null) {
                state = GitLabIssueState.OPEN;
            }

            List<Issue> issues;
            if (projectId != null) {
                // 使用 IssuesApi.getProjectIssues() 方法获取项目的 issues
                // 该方法需要传入项目ID和其他过滤参数s
                issues = issuesApi.getProjectIssues(projectId, 
                        IssueFilter.builder()
                            .withState(state.getValue())
                            .withLabels(labels)
                            .withMilestone(milestone)
                            .withScope(scope != null ? scope.getValue() : null)
                            .withAuthorUsername(author)
                            .withAssigneeUsername(assignee)
                            .withSearch(search)
                            .withConfidential(confidential)
                            .withCreatedAfter(createdAfter != null ? toGitLabDateFormat(createdAfter) : null)
                            .withCreatedBefore(createdBefore != null ? toGitLabDateFormat(createdBefore) : null)
                            .withUpdatedAfter(updatedAfter != null ? toGitLabDateFormat(updatedAfter) : null)
                            .withUpdatedBefore(updatedBefore != null ? toGitLabDateFormat(updatedBefore) : null)
                            .build());
            } else if (groupId != null) {
                // 使用 IssuesApi.getGroupIssues() 方法获取组的 issues
                // 该方法需要传入组ID和其他过滤参数
                issues = issuesApi.getGroupIssues(groupId,
                        IssueFilter.builder()
                            .withState(state.getValue())
                            .withLabels(labels)
                            .withMilestone(milestone)
                            .withScope(scope != null ? scope.getValue() : null)
                            .withAuthorUsername(author)
                            .withAssigneeUsername(assignee)
                            .withSearch(search)
                            .withConfidential(confidential)
                            .withCreatedAfter(createdAfter != null ? toGitLabDateFormat(createdAfter) : null)
                            .withCreatedBefore(createdBefore != null ? toGitLabDateFormat(createdBefore) : null)
                            .withUpdatedAfter(updatedAfter != null ? toGitLabDateFormat(updatedAfter) : null)
                            .withUpdatedBefore(updatedBefore != null ? toGitLabDateFormat(updatedBefore) : null)
                            .build());
            } else {
                throw new IllegalStateException("Either projectId or groupId must be provided");
            }

            // Filter by IIDs if provided
            if (iids != null && !iids.isEmpty()) {
                issues = issues.stream()
                        .filter(issue -> iids.contains(issue.getIid()))
                        .toList();
            }

            return issues.stream()
                    .map(this::buildDocumentFromIssue)
                    .toList();

        } catch (GitLabApiException e) {
            throw new RuntimeException("Failed to load issues from GitLab", e);
        }
    }
} 