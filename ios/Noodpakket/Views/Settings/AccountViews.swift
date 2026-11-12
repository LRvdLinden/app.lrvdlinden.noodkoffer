import SwiftUI

/// Settings section for the optional account and the household.
struct AccountSettingsSection: View {
    @State private var account = AccountService.shared
    @State private var sheet: SignInSheet.Mode?

    var body: some View {
        Section {
            if account.isSignedIn {
                NavigationLink {
                    AccountView()
                } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(account.me?.household?.name ?? String(localized: "My household"))
                            .font(.headline)
                        Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
                if let invites = account.me?.invites, !invites.isEmpty {
                    NavigationLink {
                        AccountView()
                    } label: {
                        Label("\(invites.count) invitation(s) waiting", systemImage: "envelope.badge")
                            .foregroundStyle(Theme.accent)
                    }
                }
            } else {
                Button { sheet = .signUp } label: {
                    Label("Create a free account", systemImage: "person.crop.circle.badge.plus")
                }
                Button { sheet = .signIn } label: {
                    Label("Sign in", systemImage: "person.crop.circle")
                }
            }
        } header: {
            Text("Account and household")
        } footer: {
            if account.isSignedIn {
                Text(syncLine)
            } else {
                Text("Optional. With a free account your household shares one kit, also between iPhone and Android. Everything also works without an account.")
            }
        }
        .sheet(item: $sheet) { SignInSheet(mode: $0) }
        .task { try? await account.refresh() }
    }

    private var subtitle: String {
        let n = account.me?.household?.members.count ?? 1
        let email = account.me?.user.email ?? ""
        return n > 1 ? String(localized: "\(n) members · \(email)") : email
    }

    private var syncLine: String {
        if account.isSyncing { return String(localized: "Syncing…") }
        if let e = account.syncError { return e }
        if let d = account.lastSync { return String(localized: "Synced \(d.formatted(.relative(presentation: .named)))") }
        return String(localized: "Your kit is shared with everyone in your household.")
    }
}

/// Sign in, create an account or reset the password.
struct SignInSheet: View {
    enum Mode: String, Identifiable { case signIn, signUp; var id: String { rawValue } }

    @State var mode: Mode
    var onDone: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @State private var checkMail = false
    @State private var info: String?

    var body: some View {
        NavigationStack {
            Form {
                if checkMail {
                    Section {
                        VStack(alignment: .leading, spacing: 10) {
                            Label("Check your email", systemImage: "envelope.open.fill")
                                .font(.headline).foregroundStyle(Theme.accent)
                            Text("We sent a link to \(email). Tap it to activate your account, then sign in here.")
                        }
                        .padding(.vertical, 6)
                        Button("I have confirmed, sign in") {
                            checkMail = false
                            mode = .signIn
                        }
                        Button("Send the email again") {
                            Task { await run { try await AccountService.shared.resendConfirmation(email: email); info = String(localized: "A new email is on its way.") } }
                        }
                    } footer: {
                        Text("No email? Check your spam folder. Unconfirmed accounts are deleted after 7 days.")
                    }
                } else {
                    Section {
                        Picker("Mode", selection: $mode) {
                            Text("Sign in").tag(Mode.signIn)
                            Text("New account").tag(Mode.signUp)
                        }
                        .pickerStyle(.segmented)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                    }
                    Section {
                        if mode == .signUp {
                            TextField("First name (optional)", text: $name)
                                .textContentType(.givenName)
                        }
                        TextField("Email address", text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        SecureField("Password", text: $password)
                            .textContentType(mode == .signUp ? .newPassword : .password)
                    } footer: {
                        if mode == .signUp {
                            Text("At least 8 characters, with a letter and a number. You will receive an email to confirm your address; the account is active after that.")
                        }
                    }
                    Section {
                        Button {
                            Task { await submit() }
                        } label: {
                            HStack {
                                Text(mode == .signUp ? "Create account" : "Sign in").fontWeight(.semibold)
                                Spacer()
                                if busy { ProgressView() }
                            }
                        }
                        .disabled(busy || email.isEmpty || password.isEmpty)
                        if mode == .signIn {
                            Button("Forgot password?") {
                                Task { await run { try await AccountService.shared.forgotPassword(email: email); info = String(localized: "If there is an account for this address, you will receive an email to choose a new password.") } }
                            }
                            .disabled(busy || email.isEmpty)
                        }
                    }
                }
                if let error {
                    Section { Text(error).foregroundStyle(Theme.danger) }
                }
                if let info {
                    Section { Text(info).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle(mode == .signUp ? "New account" : "Sign in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .onChange(of: mode) { _, _ in error = nil; info = nil }
        }
        .tint(Theme.accent)
    }

    private func submit() async {
        await run {
            if mode == .signUp {
                try await AccountService.shared.signUp(name: name, email: email, password: password)
                withAnimation { checkMail = true }
                onDone()
            } else {
                do {
                    try await AccountService.shared.signIn(email: email, password: password)
                    onDone()
                    dismiss()
                } catch let f as AccountService.Failure where f.code == "not_confirmed" {
                    withAnimation { checkMail = true }
                }
            }
        }
    }

    private func run(_ work: () async throws -> Void) async {
        busy = true
        error = nil
        info = nil
        defer { busy = false }
        do { try await work() } catch is CancellationError {} catch { self.error = error.localizedDescription }
    }
}

/// Account details: the household, its members and invitations, and the account itself.
struct AccountView: View {
    @State private var account = AccountService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var error: String?
    @State private var showInvite = false
    @State private var inviteEmail = ""
    @State private var showRename = false
    @State private var newName = ""
    @State private var joining: AccountService.Invite?
    @State private var removing: AccountService.Member?
    @State private var confirmLeave = false
    @State private var showDelete = false
    @State private var deletePassword = ""
    @State private var info: String?

    var body: some View {
        Form {
            if let invites = account.me?.invites, !invites.isEmpty {
                Section {
                    ForEach(invites) { invite in
                        VStack(alignment: .leading, spacing: 8) {
                            Text("\(invite.from) invites you to \(invite.householdName)").font(.headline)
                            HStack {
                                Button("Join") { joining = invite }.buttonStyle(.borderedProminent)
                                Button("Decline", role: .destructive) { act { try await account.decline(invite) } }.buttonStyle(.bordered)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text("Invitations")
                } footer: {
                    Text("When you join, the shared kit replaces the kit on this device.")
                }
            }

            if let h = account.me?.household {
                Section {
                    Button {
                        newName = h.name
                        showRename = true
                    } label: {
                        LabeledContent("Name", value: h.name)
                    }
                    .foregroundStyle(.primary)
                    ForEach(h.members) { m in
                        HStack {
                            Image(systemName: m.owner ? "person.crop.circle.badge.checkmark" : "person.crop.circle")
                                .foregroundStyle(Theme.accent)
                            VStack(alignment: .leading) {
                                Text(m.me ? String(localized: "\(m.displayName) (you)") : m.displayName)
                                if !m.name.isEmpty { Text(m.email).font(.caption).foregroundStyle(.secondary) }
                            }
                            Spacer()
                            if m.owner { Text("Owner").font(.caption).foregroundStyle(.secondary) }
                        }
                        .swipeActions {
                            if h.isOwner && !m.me {
                                Button("Remove", role: .destructive) { removing = m }
                            }
                        }
                    }
                    ForEach(h.invites) { inv in
                        HStack {
                            Image(systemName: "envelope").foregroundStyle(.secondary)
                            Text(inv.email).foregroundStyle(.secondary)
                            Spacer()
                            Text("Invited").font(.caption).foregroundStyle(.secondary)
                        }
                        .swipeActions {
                            Button("Cancel invitation", role: .destructive) { act { try await account.cancelInvite(inv.id) } }
                        }
                    }
                    Button {
                        inviteEmail = ""
                        showInvite = true
                    } label: {
                        Label("Invite someone", systemImage: "person.badge.plus")
                    }
                } header: {
                    Text("Household members")
                } footer: {
                    Text("Everyone in the household sees and edits the same kit, shopping list and emergency plan.")
                }
            }

            Section {
                Button {
                    Task { await account.syncNow() }
                } label: {
                    HStack {
                        Label("Sync now", systemImage: "arrow.triangle.2.circlepath")
                        Spacer()
                        if account.isSyncing { ProgressView() }
                    }
                }
                if let d = account.lastSync {
                    LabeledContent("Last synced", value: d.formatted(date: .omitted, time: .shortened))
                }
            }

            Section("Account") {
                LabeledContent("Email", value: account.me?.user.email ?? "")
                if (account.me?.household?.members.count ?? 1) > 1 {
                    Button("Leave household") { confirmLeave = true }
                }
                Button("Sign out") {
                    Task { await account.signOut(); dismiss() }
                }
                Button("Delete account", role: .destructive) {
                    deletePassword = ""
                    showDelete = true
                }
            }

            if let error {
                Section { Text(error).foregroundStyle(Theme.danger) }
            }
            if let info {
                Section { Text(info).foregroundStyle(.secondary) }
            }
        }
        .navigationTitle("Account")
        .refreshable { try? await account.refresh(); await account.syncNow() }
        .task { try? await account.refresh() }
        .alert("Invite someone", isPresented: $showInvite) {
            TextField("Email address", text: $inviteEmail)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Send invitation") {
                let mail = inviteEmail
                act {
                    try await account.invite(email: mail)
                    info = String(localized: "Invitation sent to \(mail).")
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("They receive an email and see the invitation in the app after signing in with this address.")
        }
        .alert("Household name", isPresented: $showRename) {
            TextField("Name", text: $newName)
            Button("Save") { let n = newName; act { try await account.renameHousehold(n) } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Join this household?", isPresented: Binding(get: { joining != nil }, set: { if !$0 { joining = nil } }), titleVisibility: .visible) {
            Button("Join and replace my kit", role: .destructive) {
                if let invite = joining { act { try await account.accept(invite) } }
            }
        } message: {
            Text("The shared kit replaces the kit on this device.")
        }
        .confirmationDialog("Remove from household?", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }), titleVisibility: .visible) {
            Button("Remove", role: .destructive) {
                if let m = removing { act { try await account.remove(m) } }
            }
        } message: {
            Text("They keep their account and start again with their own household.")
        }
        .confirmationDialog("Leave household?", isPresented: $confirmLeave, titleVisibility: .visible) {
            Button("Leave", role: .destructive) { act { try await account.leave() } }
        } message: {
            Text("You keep the kit on this device and continue with your own household.")
        }
        .alert("Delete account?", isPresented: $showDelete) {
            SecureField("Password", text: $deletePassword)
            Button("Delete", role: .destructive) {
                let pw = deletePassword
                act {
                    try await account.deleteAccount(password: pw)
                    dismiss()
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Your account is deleted immediately. If you are the last member of your household, the shared data is deleted too. The kit on this device stays.")
        }
    }

    private func act(_ work: @escaping () async throws -> Void) {
        Task {
            error = nil
            info = nil
            do { try await work() } catch is CancellationError {} catch { self.error = error.localizedDescription }
        }
    }
}
