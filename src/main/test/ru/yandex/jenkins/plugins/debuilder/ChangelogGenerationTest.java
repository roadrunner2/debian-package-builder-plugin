package ru.yandex.jenkins.plugins.debuilder;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static ru.yandex.jenkins.plugins.debuilder.ChangesExtractor.Change;

/**
 * @author: tIGO
 */
public class ChangelogGenerationTest {
	public final PersonIdent alice = new PersonIdent("Alice", "alice@alice.com");
	public final PersonIdent bob = new PersonIdent("Bob", "bob@bob.com");

	private FilePath gitDirPath;
	private GitClient git;
	private File gitDir;

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	@Rule
	public TemporaryFolder tmpFolder = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		TaskListener taskListener = jenkinsRule.createTaskListener();
		EnvVars envVars = new EnvVars();
		gitDir = tmpFolder.getRoot();
		gitDirPath = new FilePath(gitDir);
		git = Git.with(taskListener, envVars).in(gitDirPath).getClient();
		git.init();
	}

	public void commit(final File file, final String fileContent, final PersonIdent author, final String msg) throws GitException, InterruptedException {
		FilePath filePath = new FilePath(file);
		try {
			filePath.write(fileContent, null);
		} catch (Exception e) {
			throw new GitException("unable to write file", e);
		}
		String path = filePath.getRemote().replaceFirst(gitDir.getAbsolutePath() + "/", "");
		git.add(path);
		git.setAuthor(author);
		git.setCommitter(author);
		git.commit(msg);
	}

	@WithoutJenkins
	@Test
	public void testGetChangesFromGitWithNoCommitsAfterLastChangelogModification() throws IOException, InterruptedException {
		File debian = tmpFolder.newFolder("debian");
		commit(tmpFolder.newFile("1"), "1", alice, "add 1");
		commit(new File(debian, "changelog"), "changelog", alice, "init");

		List<Change> changes = ChangesExtractor.getChangesFromGit(git, gitDirPath, debian.getAbsolutePath());
		assertThat(changes, empty());
	}

	@WithoutJenkins
	@Test
	public void testGetChangesFromGitWithCommitsAfterLastChangelogModification() throws IOException, InterruptedException {
		File debian = tmpFolder.newFolder("debian");
		commit(tmpFolder.newFile("1"), "1", alice, "add 1");
		commit(new File(debian, "changelog"), "changelog", alice, "init");
		commit(tmpFolder.newFile("2"), "2", alice, "add 2");
		commit(tmpFolder.newFile("3"), "3", bob, "add 3");

		List<Change> changes = ChangesExtractor.getChangesFromGit(git, gitDirPath, debian.getAbsolutePath());
		assertThat(changes, contains(new Change(alice.getName(), "add 2"), new Change(bob.getName(), "add 3")));
	}

	@WithoutJenkins
	@Test
	public void testGetChangesFromGitWithSeveralChangelogEntries() throws IOException, InterruptedException {
		File debian = tmpFolder.newFolder("debian");
		commit(tmpFolder.newFile("1"), "1", alice, "add 1");
		commit(new File(debian, "changelog"), "changelog", alice, "init");
		commit(tmpFolder.newFile("2"), "2", alice, "add 2");
		commit(new File(debian, "changelog"), "modificate changelog", alice, "change");
		commit(tmpFolder.newFile("3"), "3", alice, "add 3");

		List<Change> changes = ChangesExtractor.getChangesFromGit(git, gitDirPath, debian.getAbsolutePath());
		assertThat(changes, contains(new Change(alice.getName(), "add 3")));
	}

}
