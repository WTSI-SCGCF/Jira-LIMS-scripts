package uk.ac.sanger.scgcf.jira.lims.post_functions

/**
 * A {@code FunctionalInterface} for actions executed on a specific issue.
 *
 * Created by ke4 on 16/02/2017.
 */
@FunctionalInterface
interface IssueAction {
    public void execute();
}