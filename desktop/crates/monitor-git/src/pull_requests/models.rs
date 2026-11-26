#[derive(Debug, Deserialize)]
struct GhPrAuthor {
    #[serde(default)]
    login: String,
}

#[derive(Debug, Deserialize)]
struct GhPrItem {
    number: u64,
    #[serde(default)]
    title: String,
    #[serde(default)]
    author: Option<GhPrAuthor>,
    #[serde(rename = "headRefName", default)]
    head_ref_name: String,
    #[serde(rename = "baseRefName", default)]
    base_ref_name: String,
    #[serde(rename = "updatedAt", default)]
    updated_at: String,
    #[serde(default)]
    url: String,
    #[serde(default)]
    state: String,
    #[serde(rename = "isDraft", default)]
    is_draft: bool,
}

#[derive(Debug, Deserialize)]
struct ApiPrUser {
    #[serde(default)]
    login: String,
}

#[derive(Debug, Deserialize)]
struct ApiPrRef {
    #[serde(rename = "ref", default)]
    branch_ref: String,
}

#[derive(Debug, Deserialize)]
struct ApiPrItem {
    number: u64,
    #[serde(default)]
    title: String,
    #[serde(default)]
    user: Option<ApiPrUser>,
    #[serde(default)]
    head: Option<ApiPrRef>,
    #[serde(default)]
    base: Option<ApiPrRef>,
    #[serde(rename = "updated_at", default)]
    updated_at: String,
    #[serde(rename = "html_url", default)]
    html_url: String,
    #[serde(default)]
    state: String,
    #[serde(default)]
    draft: bool,
}

#[derive(Debug, Deserialize)]
struct GhParentOwner {
    #[serde(default)]
    login: String,
}

#[derive(Debug, Deserialize)]
struct GhRepoParent {
    #[serde(default)]
    name: String,
    #[serde(default)]
    owner: Option<GhParentOwner>,
}

#[derive(Debug, Deserialize)]
struct GhRepoMeta {
    #[serde(rename = "isFork", default)]
    is_fork: bool,
    #[serde(default)]
    parent: Option<GhRepoParent>,
}
