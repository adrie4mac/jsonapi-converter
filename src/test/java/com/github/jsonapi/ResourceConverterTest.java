package com.github.jsonapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.github.jsonapi.models.Article;
import com.github.jsonapi.models.Author;
import com.github.jsonapi.models.Comment;
import com.github.jsonapi.models.Status;
import com.github.jsonapi.models.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * Testing functionality of JSON API converter.
 *
 * @author jbegic
 */
public class ResourceConverterTest {
	private ResourceConverter converter;

	@Before
	public void setup() {
		converter = new ResourceConverter(Status.class, User.class);
	}

	@Test
	public void testReadWriteObject() throws Exception {
		Status status = new Status();
		status.setContent("content");
		status.setCommentCount(1);
		status.setLikeCount(10);
		status.setId("id");
		status.setUser(new User());
		status.getUser().setId("userid");
		status.setRelatedUser(status.getUser());

		byte [] rawData = converter.writeObject(status);

		Assert.assertNotNull(rawData);
		Assert.assertFalse(rawData.length == 0);

		Status converted = converter.readObject(rawData, Status.class);

		// Make sure relationship with disabled serialisation is not present
		Assert.assertNull(converted.getRelatedUser());

		Assert.assertEquals(status.getId(), converted.getId());
		Assert.assertEquals(status.getLikeCount(), converted.getLikeCount());
		Assert.assertEquals(status.getCommentCount(), converted.getCommentCount());
		Assert.assertEquals(status.getContent(), converted.getContent());


		Assert.assertNotNull(converted.getUser());
		Assert.assertEquals(status.getUser().getId(), converted.getUser().getId());
	}

	@Test
	public void testReadWithIncludedSection() throws IOException {
		String apiResponse = IOUtils.getResourceAsString("status.json");

		Status status = converter.readObject(apiResponse.getBytes(), Status.class);

		Assert.assertNotNull(status.getUser());
		Assert.assertEquals("john", status.getUser().getName());

		Assert.assertNotNull(status.getUser().getStatuses());
		Assert.assertEquals(2, status.getUser().getStatuses().size());
	}

	@Test
	public void testResolverGlobal() throws IOException {
		converter.setGlobalResolver(new RelationshipResolver() {
			@Override
			public byte[] resolve(String relationshipURL) {
				try {
					return IOUtils.getResourceAsString("user-liz.json").getBytes();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		String apiResponse = IOUtils.getResourceAsString("status.json");

		Status status = converter.readObject(apiResponse.getBytes(), Status.class);

		Assert.assertNotNull(status.getUser());
		Assert.assertEquals("liz", status.getUser().getName());
	}

	@Test
	public void testResolverTyped() throws IOException {
		converter.setGlobalResolver(new RelationshipResolver() {
			@Override
			public byte[] resolve(String relationshipURL) {
				try {
					return IOUtils.getResourceAsString("user-liz.json").getBytes();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		converter.setTypeResolver(new RelationshipResolver() {
			@Override
			public byte[] resolve(String relationshipURL) {
				try {
					return IOUtils.getResourceAsString("user-john.json").getBytes();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}, User.class);

		String apiResponse = IOUtils.getResourceAsString("status.json");

		Status status = converter.readObject(apiResponse.getBytes(), Status.class);

		Assert.assertNotNull(status.getUser());
		Assert.assertEquals("john", status.getUser().getName());
	}

	@Test
	public void testReadCollection() throws IOException {
		String apiResponse = IOUtils.getResourceAsString("users.json");

		List<User> users = converter.readObjectCollection(apiResponse.getBytes(), User.class);

		Assert.assertEquals(2, users.size());

		Assert.assertEquals("1", users.get(0).getId());
		Assert.assertEquals("liz", users.get(0).getName());

		Assert.assertEquals("2", users.get(1).getId());
		Assert.assertEquals("john", users.get(1).getName());

	}

	@Test
	public void testReadWithCollectionRelationship() throws IOException {
		String apiResponse = IOUtils.getResourceAsString("user-with-statuses.json");

		User user = converter.readObject(apiResponse.getBytes(), User.class);

		Assert.assertNotNull(user.getStatuses());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testExpectCollection() throws IOException {
		converter.readObjectCollection(IOUtils.getResourceAsString("user-with-statuses.json").getBytes(), User.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testExpectObject() throws IOException {
		converter.readObject(IOUtils.getResourceAsString("users.json").getBytes(), User.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testExpectData() {
		converter.readObject("{}".getBytes(), User.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDataNodeMustBeAnObject() {
		converter.readObject("{\"data\" : \"attribute\"}".getBytes(), User.class);
	}

	@Test
	public void testIncludedFullRelationships() throws IOException {
		String apiResponse = IOUtils.getResourceAsString("articles.json");

		ObjectMapper articlesMapper = new ObjectMapper();
		articlesMapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

		ResourceConverter articlesConverter = new ResourceConverter(articlesMapper, Article.class, Author.class,
				Comment.class);

		List<Article> articles = articlesConverter.readObjectCollection(apiResponse.getBytes(), Article.class);


		Assert.assertNotNull(articles);
		Assert.assertEquals(1, articles.size());

		Article article = articles.get(0);

		Assert.assertEquals("JSON API paints my bikeshed!", article.getTitle());
		Assert.assertEquals("1", article.getId());

		Assert.assertNotNull(article.getAuthor());

		Author author = article.getAuthor();

		Assert.assertEquals("9", author.getId());
		Assert.assertEquals("Dan", author.getFirstName());

		Assert.assertNotNull(article.getComments());

		List<Comment> comments = article.getComments();

		Assert.assertEquals(2, comments.size());

		Comment commentWithAuthor = comments.get(1);

		Assert.assertEquals("12", commentWithAuthor.getId());
		Assert.assertEquals("I like XML better", commentWithAuthor.getBody());

		Assert.assertNotNull(commentWithAuthor.getAuthor());
		Assert.assertEquals("9", commentWithAuthor.getAuthor().getId());
		Assert.assertEquals("dgeb", commentWithAuthor.getAuthor().getTwitter());
	}
}
