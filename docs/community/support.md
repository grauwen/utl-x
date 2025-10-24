# Getting Support

Need help with UTL-X? Here's how to get assistance.

---

## Community Support (Free)

### GitHub Discussions 💬

**Best for:** Questions, discussions, ideas

**Platform:** [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)

**Categories:**
- **Q&A** - Ask questions, get answers
- **Ideas** - Suggest features
- **Show and Tell** - Share your projects
- **General** - Everything else

**Response time:** Usually within 24-48 hours from community

**How to ask a good question:**

1. **Search first** - Check if already answered
2. **Be specific** - Include code examples
3. **Provide context** - What are you trying to achieve?
4. **Include details:**
   - UTL-X version
   - Input format
   - Expected vs actual output
   - Error messages

**Example good question:**

```
Title: How to filter JSON array by nested property?

I'm trying to filter a JSON array where each object has a nested 
'address' property, and I want only items where address.city == "NYC".

Input:
{
  "users": [
    {"name": "Alice", "address": {"city": "NYC"}},
    {"name": "Bob", "address": {"city": "LA"}}
  ]
}

I tried:
input.users[address.city == "NYC"]

But I get an error. What's the correct syntax?

Version: UTL-X 0.1.0
OS: macOS 13.0
```

### GitHub Issues 🐛

**Best for:** Bug reports, feature requests

**Platform:** [GitHub Issues](https://github.com/grauwen/utl-x/issues)

**When to use:**
- ✅ You found a bug
- ✅ You have a specific feature request
- ✅ Documentation is wrong or unclear
- ❌ General questions (use Discussions instead)

**Bug Report Template:**

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce:
1. Create file $input.xml with: ...
2. Run command: utlx transform ...
3. See error: ...

**Expected behavior**
What you expected to happen.

**Actual behavior**
What actually happened.

**Environment**
- UTL-X version: 0.1.0
- OS: Ubuntu 22.04
- Java version: OpenJDK 17.0.2

**Additional context**
Any other relevant information.
```

**Feature Request Template:**

```markdown
**Is your feature request related to a problem?**
Describe the problem or limitation.

**Describe the solution you'd like**
What you want to happen.

**Describe alternatives you've considered**
Other solutions you thought about.

**Use case**
Real-world example of how this would be used.

**Additional context**
Any other relevant information.
```

### Email Support 📧

**Best for:** General inquiries, private questions

**Email:** community@glomidco.com

**Response time:** 2-3 business days

**Use email for:**
- Questions you prefer to keep private
- General inquiries about the project
- Partnership opportunities
- Media inquiries

---

## Documentation 📚

### Self-Service Resources

**Start here before asking:**

1. **[FAQ](faq.md)** - Common questions answered
2. **[Getting Started](../getting-started/)** - Tutorials for beginners
3. **[Examples](../examples/)** - Real-world code examples
4. **[Cookbook](../examples/cookbook.md)** - Common patterns
5. **[Language Guide](../language-guide/)** - Detailed reference
6. **[API Reference](../reference/)** - Complete API docs

### Search Tips

**Using GitHub search:**
- Search issues: `is:issue keyword`
- Search discussions: `is:discussion keyword`
- Search docs: Search in the `docs/` folder

**Google search:**
```
site:github.com/grauwen/utl-x keyword
```

---

## Commercial Support 💼

### Priority Support

**For commercial license customers**

**Includes:**
- Priority response times (4-24 hours)
- Direct email support
- Phone/video support (Enterprise tier)
- Dedicated support engineer (Enterprise tier)

**Tiers:**

| Tier | Response Time | Support Channel |
|------|---------------|-----------------|
| **Developer** | 48 hours | Email |
| **Team** | 24 hours | Email + Slack |
| **Enterprise** | 4 hours | Email + Slack + Phone |

**Contact:** support@glomidco.com

### Consulting Services 🎓

**Custom solutions and training**

**Services offered:**
- **Integration assistance** - Help integrating UTL-X
- **Custom development** - Bespoke features
- **Training workshops** - On-site or remote
- **Architecture consulting** - Design guidance
- **Migration services** - From XSLT/DataWeave

**Contact:** consulting@glomidco.com

### Commercial Licensing 💰

**Questions about commercial licenses**

**Email:** licensing@glomidco.com

**We can help with:**
- License options and pricing
- Commercial license agreements
- Volume licensing
- OEM/reseller agreements

**See:** [Commercial Licensing](https://utl-x.com/commercial)

---

## Social Media 📱

### Twitter/X

**Follow:** [@UTLXLang](https://twitter.com/UTLXLang)

**For:**
- Announcements
- Release updates
- Tips and tricks
- Community highlights

**Response time:** Best effort (not for support)

### LinkedIn

**Follow:** [UTL-X LinkedIn](https://linkedin.com/company/utlx)

**For:**
- Professional updates
- Case studies
- Job opportunities

---

## Contributing 🤝

### Help Others

**Become a community helper:**

- Answer questions in Discussions
- Review pull requests
- Improve documentation
- Share examples and tutorials
- Write blog posts

**Benefits:**
- Learn UTL-X deeply
- Build reputation
- Give back to community
- Get recognized as contributor

### Report Issues

**Found a problem?**

1. **Check if already reported** - Search existing issues
2. **Create detailed bug report** - Use issue template
3. **Provide reproducible example** - Minimal code that shows problem
4. **Be responsive** - Answer clarifying questions

### Improve Documentation

**Docs unclear?**

- Submit corrections via pull request
- Suggest improvements in issues
- Add examples to cookbook
- Translate to other languages

**See:** [Contributing Guide](../../CONTRIBUTING.md)

---

## Response Time Guidelines

### Community Support

| Channel | Response Time | Who Responds |
|---------|---------------|--------------|
| GitHub Discussions | 24-48 hours | Community + maintainers |
| GitHub Issues | 2-3 business days | Maintainers |
| Email (community) | 2-3 business days | Team |

### Commercial Support

| Tier | Response Time | Availability |
|------|---------------|--------------|
| Developer | 48 hours | Email |
| Team | 24 hours | Email + Slack |
| Enterprise | 4 hours | 24/7 |

**Note:** Response time is for initial response, not resolution time.

---

## Escalation Path

If you don't get a response:

1. **Wait** - Give it the stated response time
2. **Check spam** - Responses might be filtered
3. **Follow up** - Add comment to discussion/issue
4. **Try different channel** - Use email if no GitHub response
5. **Contact leadership** - info@glomidco.com (last resort)

---

## Support Scope

### What We Support

✅ **In scope:**
- UTL-X language questions
- Installation and setup issues
- Bug reports
- Feature requests
- Performance issues
- Documentation clarifications
- Integration questions

❌ **Out of scope:**
- General programming questions
- Third-party tool issues
- Infrastructure problems
- Custom development (use consulting)
- "Do my homework" requests

### Best Effort Support

For alpha/beta versions:
- Support is best-effort
- Breaking changes may occur
- Not suitable for production
- Community-driven support primary

For v1.0+ (stable):
- Full support commitment
- No breaking changes in minor versions
- Production-ready guarantees
- Dedicated support team

---

## Tips for Getting Quick Help

### 1. Provide Complete Information

**Include:**
- UTL-X version: `utlx --version`
- OS and version
- Java version: `java -version`
- Input data (sample)
- Transformation script
- Expected output
- Actual output or error

### 2. Create Minimal Reproducible Example

**Good example:**

```utlx
%utlx 1.0
input json
output json
---
{
  result: $input.value * 2
}
```

**Input:**
```json
{"value": 10}
```

**Expected:** `{"result": 20}`  
**Actual:** Error message

### 3. Show What You Tried

Demonstrate effort:
- "I tried X but got Y"
- "I read documentation Z"
- "I searched for similar issues"

### 4. Be Specific

**❌ Vague:**
"JSON transformation doesn't work"

**✅ Specific:**
"When transforming JSON with nested arrays using `map`, I get 'undefined' for array elements at index > 0"

### 5. Format Code Properly

Use code blocks with syntax highlighting:

```utlx
%utlx 1.0
input xml
output json
---
{
  result: $input.data
}
```

---

## Learning Resources

### Official Resources

- 📖 **[Documentation](../README.md)** - Complete docs
- 🎓 **[Getting Started](../getting-started/)** - Tutorials
- 💡 **[Examples](../examples/)** - Code samples
- 📚 **[Language Guide](../language-guide/)** - Reference

### Community Resources

- 📝 **Blog posts** - Community articles
- 🎥 **Video tutorials** - Community videos
- 💬 **Stack Overflow** - Tag: `utl-x` (coming soon)
- 🗣️ **Meetups** - Local user groups (coming soon)

### Books and Courses

*Coming soon as project matures*

---

## Report Security Issues

**Found a security vulnerability?**

**DO NOT** open a public issue.

**Email:** security@glomidco.com

**Include:**
- Description of vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

**We will:**
- Acknowledge within 24 hours
- Investigate promptly
- Keep you updated
- Credit you in security advisory (if desired)

**See:** [Security Policy](../../SECURITY.md)

---

## Feedback

### Help Us Improve Support

We want to provide excellent support!

**Tell us:**
- How we can improve documentation
- What questions come up frequently
- Where you get stuck
- What examples would help

**Feedback channels:**
- GitHub Discussions (General category)
- Email: community@glomidco.com
- Surveys (when sent)

---

## Office Hours (Future)

**Coming soon:** Monthly community office hours

- Live Q&A with maintainers
- Demo new features
- Discuss roadmap
- Help with complex problems

**Subscribe:** announce@glomidco.com

---

## Summary

**Quick reference:**

| Need | Channel | Response Time |
|------|---------|---------------|
| Question | [Discussions](https://github.com/grauwen/utl-x/discussions) | 24-48h |
| Bug Report | [Issues](https://github.com/grauwen/utl-x/issues) | 2-3 days |
| Documentation | [Docs](../README.md) | Immediate |
| Commercial | support@glomidco.com | Per SLA |
| Security | security@glomidco.com | 24h |
| General | community@glomidco.com | 2-3 days |

---

**We're here to help! Don't hesitate to reach out.** 🚀
