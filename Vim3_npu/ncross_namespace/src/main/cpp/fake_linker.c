//
// Created by lenovo-s on 2019/4/15.
//

#include "fake_linker.h"

#include <stdio.h>
#include <android/log.h>
#include <string.h>
#include <elf.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <capstone/capstone.h>
#include <assert.h>
#include <link.h>
#include <elf.h>

#if defined(__LP64__)
#define Elf_Ehdr Elf64_Ehdr
#define Elf_Shdr Elf64_Shdr
#define Elf_Sym Elf64_Sym
#else
#define Elf_Ehdr Elf32_Ehdr
#define Elf_Shdr Elf32_Shdr
#define Elf_Sym Elf32_Sym
#endif


#define TAG_NAME "fake-linker"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG_NAME, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG_NAME, __VA_ARGS__)

typedef struct {
    uint32_t nbucket;
    uint32_t nchain;

    uint32_t *bucket;
    uint32_t *chain;
} Elf32_HashTable;

typedef Elf32_HashTable Elf64_HashTable;

typedef struct {
    uint32_t nbuckets;
    uint32_t symndx;
    uint32_t maskwords;
    uint32_t shift2;

    uint32_t *bloom_filter;
    uint32_t *bucket;
    uint32_t *chain;

} Elf32_GnuHashTable;

typedef struct {
    uint32_t nbuckets;
    uint32_t symndx;
    uint32_t maskwords;
    uint32_t shift2;

    uint64_t *bloom_filter;
    uint32_t *bucket;
    uint32_t *chain;
} Elf64_GnuHashTable;

typedef struct {
    ElfW(Off) file_start_offset;
    ElfW(Addr) vaddr_start;
    ElfW(Word) file_length;
    ElfW(Word) mem_length;
} program_table_area;


typedef struct {
    ElfW(Ehdr) *p_ehdr;
    ElfW(Phdr) *p_phdr;

    ElfW(Dyn) *p_pt_dyn;

    union {
        ElfW(Rel) *p_dt_jmp_rel;
        ElfW(Rela) *p_dt_jmp_rela;
    } dt_jmprel;

    ElfW(Sym) *p_dt_sym;
    char *p_dt_strtab;

    ElfW(HashTable) hash_table;
    ElfW(GnuHashTable) gun_hash_table;

    int dt_pltrel;
    int dt_pltrel_size;
    int pt_dyn_size;
    int dt_strtab_size;
    int dt_sym_size;

    program_table_area *p_ph_areas;

} ie_find_params;

typedef struct {
    gaddress sh_strtab_offset;
    gaddress sh_strtab_addr;    // 实际内存中的地址
    gsize sh_strtab_size;    // 节区大小
    gsize str_addralign;

    gaddress sh_symtab_offset;
    gaddress sh_symtab_addr;
    gsize sh_symtab_size;
    gsize sym_entsize;
    gsize sym_addralign;
    gsize sym_num;
} SymbolParams;


static uint32_t calculate_elf_hash(const char *name);

static uint32_t calculate_elf_gnu_hash(const char *name);

static gaddress file_offset_to_vaddress(program_table_area *areas, int num, gsize offset);

static gaddress hash_lookup(ie_find_params *params, const char *name, int *sym_index);

static gaddress gnu_hash_lookup(ie_find_params *params, const char *name, int *sym_index);

static gaddress dynamic_rel_find_symbol(ie_find_params *params, int sym_index);

static gaddress dynamic_rela_find_symbol(ie_find_params *params, int sym_index);

static gaddress resolve_inner_symbol_address(const char *library_name, const char *symbol_name);


gaddress find_library_base(const char *library_name, const char *mode) {
    gaddress result = 0;
    char line[LINE_MAX], path[PATH_MAX];
    FILE *fp;
    char *name;

    name = alloca(strlen(library_name) + 2);
    if (strchr(library_name, '/')) {
        strcpy(name, library_name);
    } else {
        name[0] = '/';
        strcpy(&name[1], library_name);
    }

    fp = fopen("/proc/self/maps", "re");
    if (fp == NULL) {
        LOGE("open file /proc/self/maps error");
        return 0;
    }

    while (result == 0 && fgets(line, LINE_MAX, fp) != NULL) {
        gaddress start;
        int n;
        char *p;

        n = sscanf(line, "%llx-%*x %*s %*x %*s %*s %s", &start, path);

        if (n != 2) {
            continue;
        }

        if (!strstr(line, mode)) {
            continue;
        }

        if (path[0] == '[') {
            continue;
        }

        p = strstr(path, name);
        if (p != NULL && strlen(p) == strlen(name)) {
            result = start;
        }
    }
    fclose(fp);
    return result;
}

/*
 * 导入或导出符号会存在动态节区的符号表中,直接内存查找即可
 * 导出符号直接在symtab表中就包含了地址,而导入符号需要经过DT_JMPREL才能找到实际地址
 *
 * */
gaddress
resolve_library_symbol_address(const char *library_name, const char *symbol_name, SymbolType type) {
    gaddress base;
    ie_find_params params = {0};
    gaddress tmp = 0;
    gaddress retval = 0;
    int sym_index;

    if (type == ST_INNER){
        return resolve_inner_symbol_address(library_name, symbol_name);
    }

    base = find_library_base(library_name, "r-xp");
    if (base == 0) {
        goto end;
    }

    params.p_ehdr = (ElfW(Ehdr) *) GSIZE_TO_POINTER(base);
    params.p_ph_areas = malloc(sizeof(program_table_area) * params.p_ehdr->e_phnum);
    params.p_phdr = (ElfW(Phdr) *) (base + params.p_ehdr->e_phoff);

    for (int i = 0; i < params.p_ehdr->e_phnum; ++i, params.p_phdr++) {
        params.p_ph_areas[i].file_start_offset = params.p_phdr->p_offset;
        params.p_ph_areas[i].vaddr_start = params.p_phdr->p_vaddr;
        params.p_ph_areas[i].file_length = params.p_phdr->p_filesz;
        params.p_ph_areas[i].mem_length = params.p_phdr->p_memsz;

        if (params.p_phdr->p_type == PT_DYNAMIC) {
            params.p_pt_dyn = (ElfW(Dyn) *) GSIZE_TO_POINTER(base + params.p_phdr->p_vaddr);
            params.pt_dyn_size = params.p_phdr->p_filesz / sizeof(ElfW(Dyn));
        }
    }

    if (params.p_pt_dyn == NULL) {
        LOGE("not found dynamic segment");
        goto end;
    }

    for (int i = 0; i < params.pt_dyn_size; ++i, params.p_pt_dyn++) {
        switch (params.p_pt_dyn->d_tag) {
            case DT_SYMTAB:
                tmp = file_offset_to_vaddress(params.p_ph_areas, params.p_ehdr->e_phnum,
                                              params.p_pt_dyn->d_un.d_ptr);
                if (tmp == 0) {
                    LOGE("Failed to find symbol table(DT_SYMTAB)");
                    goto end;
                }
                params.p_dt_sym = (ElfW(Sym) *) (base + tmp);
                break;
            case DT_SYMENT:
                params.dt_sym_size = params.p_pt_dyn->d_un.d_val;
                break;
            case DT_STRTAB:
                tmp = file_offset_to_vaddress(params.p_ph_areas, params.p_ehdr->e_phnum,
                                              params.p_pt_dyn->d_un.d_ptr);
                if (tmp == 0) {
                    LOGE("Failed to find string table(DT_STRTAB)");
                    goto end;
                }
                params.p_dt_strtab = (char *) (base + tmp);
                break;
            case DT_STRSZ:
                params.dt_strtab_size = params.p_pt_dyn->d_un.d_val;
                break;
            case DT_PLTREL:
                params.dt_pltrel = params.p_pt_dyn->d_un.d_val;
                break;
            case DT_JMPREL:
                tmp = file_offset_to_vaddress(params.p_ph_areas, params.p_ehdr->e_phnum,
                                              params.p_pt_dyn->d_un.d_ptr);
                if (tmp == 0) {
                    LOGE("Failed to find jump relocation table (DT_JMPREL)");
                }
                params.dt_jmprel.p_dt_jmp_rel = (ElfW(Rel) *) (base + tmp);
                break;
            case DT_PLTRELSZ:
                params.dt_pltrel_size = params.p_pt_dyn->d_un.d_val;
                break;
            case DT_HASH:
                tmp = file_offset_to_vaddress(params.p_ph_areas, params.p_ehdr->e_phnum,
                                              params.p_pt_dyn->d_un.d_ptr);
                if (tmp == 0) {
                    LOGE("Failed to find hash table (DT_HASH)");
                    goto end;
                }
                tmp += base;
                params.hash_table.nbucket = *(uint32_t *) tmp;
                params.hash_table.nchain = *(uint32_t *) (tmp + sizeof(uint32_t));
                params.hash_table.bucket = (uint32_t *) (tmp + 2 * sizeof(uint32_t));
                params.hash_table.chain = &params.hash_table.bucket[params.hash_table.nbucket];
                break;
            case DT_GNU_HASH:
                tmp = file_offset_to_vaddress(params.p_ph_areas, params.p_ehdr->e_phnum,
                                              params.p_pt_dyn->d_un.d_ptr);
                if (tmp == 0) {
                    LOGE("Failed to find gnu hash table (DT_GNU_HASH)");
                    goto end;
                }
                tmp += base;
                params.gun_hash_table.nbuckets = *(uint32_t *) tmp;
                params.gun_hash_table.symndx = *(uint32_t *) (tmp + sizeof(uint32_t));
                params.gun_hash_table.maskwords = *(uint32_t *) (tmp + sizeof(uint32_t) * 2);
                params.gun_hash_table.shift2 = *(uint32_t *) (tmp + sizeof(uint32_t) * 3);
#if defined(__LP64__)
                params.gun_hash_table.bloom_filter = (uint64_t *) (tmp + sizeof(uint32_t) * 4);
#else
                params.gun_hash_table.bloom_filter = (uint32_t *) (tmp + sizeof(uint32_t) * 4);
#endif
                params.gun_hash_table.bucket = &params.gun_hash_table.bloom_filter[params.gun_hash_table.maskwords];
                params.gun_hash_table.chain = &params.gun_hash_table.bucket[params.gun_hash_table.nbuckets];
                break;
            default:
                break;
        }
    }

    if (params.hash_table.nbucket == 0 && params.gun_hash_table.nbuckets == 0) {
        LOGE("not found DT_HASH or DT_GNU_HASH, unable to find symbol");
        goto end;
    }
    if (params.p_dt_sym == NULL) {
        LOGE("not found DT_SYMTAB, can't continue");
        goto end;
    }
    if (params.p_dt_strtab == NULL) {
        LOGE("not found DT_STRTAB, can't continue");
        goto end;
    }
    // 有hash_table就用hash_table,否则用gnu_hash_table

    if (params.hash_table.nbucket != 0) {
        retval = hash_lookup(&params, symbol_name, &sym_index);
    } else {
        retval = gnu_hash_lookup(&params, symbol_name, &sym_index);
    }
    // 不管符号是否导出都会存在符号表中,找不到则说明没有该符号
    if (retval != 0) {
        retval = base + retval;
        goto end;
    }
    if (sym_index == -1 || type == ST_EXPORTED) {
        goto end;
    }

    // 此时再查找JMPREL导入表
    if (params.dt_pltrel == DT_REL) {
        retval = dynamic_rel_find_symbol(&params, sym_index);
        retval = retval == 0 ? 0 : base + retval;
    } else if (params.dt_pltrel == DT_RELA) {
        retval = dynamic_rela_find_symbol(&params, sym_index);
        retval = retval == 0 ? 0 : base + retval;
    } else {
        LOGE("unknown pltrel type, only support DT_REL(0x11), DT_RELA(0x7), actual type: 0x%x",
             params.dt_pltrel);
        goto end;
    }

    end:
    free(params.p_ph_areas);
    return retval;
}

gpointer resolve_inner_dlopen_or_dlsym(gpointer fun) {
    gpointer impl;
    csh capstone;
    cs_err err;
    gsize dlopen_address;
    cs_insn *insn;
    size_t count;
    gaddress pic;
    gaddress *pic_value;

    pic_value = &pic;
    impl = fun;
    *pic_value = 0;
#if defined(__i386__)
    err = cs_open(CS_ARCH_X86, CS_MODE_32, &capstone);
    assert(err == CS_ERR_OK);
    err = cs_option(capstone, CS_OPT_DETAIL, CS_OPT_ON);
    assert(err == CS_ERR_OK);

    dlopen_address = GPOINTER_TO_SIZE(impl);

    insn = NULL;
    count = cs_disasm(capstone, GSIZE_TO_POINTER(dlopen_address), 48, dlopen_address, 18, &insn);

    for (size_t i = 0; i != count; ++i) {
        const cs_insn *cur = &insn[i];
        const cs_x86_op *op1 = &cur->detail->x86.operands[0];
        const cs_x86_op *op2 = &cur->detail->x86.operands[1];

        switch (cur->id) {
            case X86_INS_CALL:
                if (op1->type == X86_OP_IMM) {
                    impl = GSIZE_TO_POINTER(op1->imm);
                }
                break;
            case X86_INS_POP:
                if (op1->reg == X86_REG_EBX && *pic_value == 0) {
                    *pic_value = cur->address;
                }
                break;
            case X86_INS_ADD:
                if (op1->reg == X86_REG_EBX) {
                    *pic_value += op2->imm;
                }
                break;
            default:
                break;
        }
    }

    if (impl != fun) {
        // plt表
        count = cs_disasm(capstone, impl, 6, GPOINTER_TO_SIZE(impl), 1, &insn);
        assert(count == 1);
        const cs_x86_op op1 = insn[0].detail->x86.operands[0];
        // jmp, [ebx + #imm32]
        if (insn[0].id == X86_INS_JMP && op1.mem.base == X86_REG_EBX) {
            gpointer tmp = GSIZE_TO_POINTER(*pic_value + op1.mem.disp);
            // tmp为got表地址,取值就得到真实函数地址
            gsize addr = *(gsize *) tmp;
            impl = GSIZE_TO_POINTER(addr);
        }
    } else {
        impl = NULL;
    }
#elif defined(__x86_64__)
    err = cs_open(CS_ARCH_X86, CS_MODE_64, &capstone);
    assert(err == CS_ERR_OK);

    err = cs_option(capstone, CS_OPT_DETAIL, CS_OPT_ON);
    assert(err == CS_ERR_OK);

    dlopen_address = GPOINTER_TO_SIZE(impl);

    insn = NULL;
    count = cs_disasm(capstone, GSIZE_TO_POINTER(dlopen_address), 16, dlopen_address, 4, &insn);

    for (size_t i = 0; i != count; i++) {
        const cs_insn * cur = &insn[i];
        const cs_x86_op * op = &cur->detail->x86.operands[0];

        if (cur->id == X86_INS_JMP) {
            if (op->type == X86_OP_IMM) {
                impl = GSIZE_TO_POINTER(op->imm);
            }
            break;
        }
    }
    if (impl != fun){
        count = cs_disasm(capstone, impl, 6, GPOINTER_TO_SIZE(impl), 1, &insn);
        assert(count == 1);
        const cs_x86_op op1 = insn[0].detail->x86.operands[0];

        if (insn[0].id == X86_INS_JMP && op1.mem.base == X86_REG_RIP){
            // jmp, [rip + #imm32]
            // 这里加6是因为rip指向的是下一条要执行的指令,而当前指令占6个字节
            gpointer tmp= GSIZE_TO_POINTER(GPOINTER_TO_SIZE(impl) + 6 + op1.mem.disp);

            gsize addr = *(gsize *)tmp;
            impl = GSIZE_TO_POINTER(addr);
        }
    } else{
        impl = NULL;
    }

#elif defined(__arm__)
    err = cs_open(CS_ARCH_ARM, CS_MODE_THUMB, &capstone);
    assert(err == CS_ERR_OK);

    err = cs_option(capstone, CS_OPT_DETAIL, CS_OPT_ON);
    assert(err == CS_ERR_OK);

    dlopen_address = GPOINTER_TO_SIZE(impl) & (gsize) ~1;

    insn = NULL;
    count = cs_disasm(capstone, GSIZE_TO_POINTER(dlopen_address), 10, dlopen_address, 4, &insn);
    if (count == 4 &&
        insn[0].id == ARM_INS_PUSH &&
        (insn[1].id == ARM_INS_MOV &&
         insn[1].detail->arm.operands[0].reg == ARM_REG_R2 &&
         insn[1].detail->arm.operands[1].reg == ARM_REG_LR) &&
        (insn[2].id == ARM_INS_BL || insn[2].id == ARM_INS_BLX) &&
        insn[3].id == ARM_INS_POP) {
        gsize thumb_bit = (insn[2].id == ARM_INS_BL) ? 1 : 0;
        impl = GSIZE_TO_POINTER(insn[2].detail->arm.operands[0].imm | thumb_bit);
    }
#elif defined(__aarch64__)
    err = cs_open(CS_ARCH_ARM64, CS_MODE_ARM, &capstone);
    assert(err == CS_ERR_OK);

    err = cs_option(capstone, CS_OPT_DETAIL, CS_OPT_ON);
    assert(err == CS_ERR_OK);

    dlopen_address = GPOINTER_TO_SIZE(impl);

    insn = NULL;
    count = cs_disasm(capstone, GSIZE_TO_POINTER(dlopen_address), 6 * sizeof(uint32_t),
                      dlopen_address, 6, &insn);
    if (count == 6 &&
        insn[0].id == ARM64_INS_STP &&
        insn[1].id == ARM64_INS_MOV &&
        (insn[2].id == ARM64_INS_MOV &&
         insn[2].detail->arm64.operands[0].reg == ARM64_REG_X2 &&
         insn[2].detail->arm64.operands[1].reg == ARM64_REG_LR ||
         insn->detail->arm64.operands[1].reg == ARM64_REG_X30) &&
        insn[3].id == ARM64_INS_BL &&
        insn[4].id == ARM64_INS_LDP &&
        insn[5].id == ARM64_INS_RET) {
        impl = GSIZE_TO_POINTER(insn[3].detail->arm64.operands[0].imm);
    }
#else
#error Unsupported architecture
#endif
    cs_free(insn, count);
    cs_close(&capstone);
    return impl;
}

/*
 * 内部符号不存在内存镜像中,必须走库文件的节区查找
 *
 * */
static gaddress resolve_inner_symbol_address(const char *library_name, const char *symbol_name) {
    int fd = -1;
    Elf_Ehdr *ehdr;
    gsize size;
    SymbolParams params = {0};
    gsize shoff;
    Elf_Shdr *sh;
    Elf_Sym *sym;
    gaddress retval = 0;


    fd = open(library_name, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        LOGE("open file %s error", library_name);
        return 0;
    }

    size = (gsize) lseek(fd, 0, SEEK_END);

    ehdr = (Elf_Ehdr *) mmap(0, size, PROT_READ, MAP_SHARED, fd, 0);
    if (ehdr == NULL) {
        LOGE("mmap file %s failed", library_name);
        goto fail;
    }
    shoff = (gsize) ((void *) ehdr + ehdr->e_shoff);

    for (int i = 0; i < ehdr->e_shnum; ++i, shoff += ehdr->e_shentsize) {
        sh = (Elf_Shdr *) shoff;
        switch (sh->sh_type) {
            case SHT_STRTAB:
                params.sh_strtab_addr = (gaddress) ((void *) ehdr + sh->sh_offset);
                params.sh_strtab_size = sh->sh_size;
                break;
            case SHT_SYMTAB:
                params.sh_symtab_addr = (gaddress) ((void *) ehdr + sh->sh_offset);
                params.sym_num = sh->sh_size / sh->sh_entsize;
                params.sym_entsize = sh->sh_entsize;
                break;
            default:
                break;
        }
    }
    if (params.sh_symtab_addr == 0 || params.sh_strtab_addr == 0) {
        LOGE("not found strtab %llx or symtab %llx", params.sh_strtab_addr, params.sh_symtab_addr);
        retval = 0;
        goto mem_error;
    }

    for (int i = 0; i < params.sym_num; ++i) {
        sym = (Elf_Sym *) (params.sh_symtab_addr + i * params.sym_entsize);
        if (strcmp(symbol_name, (const char *) (params.sh_strtab_addr + sym->st_name)) == 0) {
            retval = sym->st_value;
            break;
        }
    }

    mem_error:
    {
        munmap(ehdr, size);
    }

    fail:
    {
        close(fd);
        if (retval != 0) {
            retval += find_library_base(library_name, "r-xp");
        }
        return retval;
    }
}


static gaddress dynamic_rel_find_symbol(ie_find_params *params, int sym_index) {
    ElfW(Rel) *s;

    s = params->dt_jmprel.p_dt_jmp_rel;
    for (int i = 0; i < params->dt_pltrel_size / sizeof(ElfW(Rel)); ++i, s++) {
#if defined(__LP64__)
        if (ELF64_R_SYM(s->r_info) == sym_index){
            return s->r_offset;
        }
#else
        if (ELF32_R_SYM(s->r_info) == sym_index) {
            return s->r_offset;
        }
#endif
    }
    return 0;
}

static gaddress dynamic_rela_find_symbol(ie_find_params *params, int sym_index) {
    ElfW(Rela) *s;

    s = params->dt_jmprel.p_dt_jmp_rela;
    for (int i = 0; i < params->dt_pltrel_size / sizeof(ElfW(Rela)); ++i, s++) {
#if defined(__LP64__)
        if (ELF64_R_SYM(s->r_info) == sym_index){
            return s->r_offset;
        }
#else
        if (ELF32_R_SYM(s->r_info) == sym_index) {
            return s->r_offset;
        }
#endif
    }
    return 0;
}

static gaddress hash_lookup(ie_find_params *params, const char *name, int *sym_index) {
    uint32_t hash = 0, n;
    ElfW(Sym) *s;

    hash = calculate_elf_hash(name);
    for (n = params->hash_table.bucket[hash % params->hash_table.nbucket];
         n != 0; n = params->hash_table.chain[n]) {
        s = params->p_dt_sym + n;
        if (strcmp(params->p_dt_strtab + s->st_name, name) == 0) {
            *sym_index = n;
            return s->st_value;
        }
    }
    *sym_index = -1;
    return 0;
}

static gaddress gnu_hash_lookup(ie_find_params *params, const char *name, int *sym_index) {
    uint32_t hash, h2, n;
    uint32_t bloom_mask_bits = sizeof(ElfW(Addr)) * 8;
    uint32_t word_num;
    ElfW(Addr) bloom_word;
    ElfW(Sym) *s;

    hash = calculate_elf_gnu_hash(name);
    h2 = hash >> params->gun_hash_table.shift2;

    word_num = (hash / bloom_mask_bits) & params->gun_hash_table.maskwords;
    bloom_word = params->gun_hash_table.bloom_filter[word_num];
    if ((1 & (bloom_word >> (hash % bloom_mask_bits)) & (bloom_word >> (h2 % bloom_mask_bits))) ==
        0) {
        goto find_undefine;
    }

    n = params->gun_hash_table.bucket[hash % params->gun_hash_table.nbuckets];
    if (n == 0) {
        goto find_undefine;
    }
    do {
        s = params->p_dt_sym + n;
        if (((params->gun_hash_table.chain[n] ^ hash) >> 1) == 0 &&
            strcmp(params->p_dt_strtab + s->st_name, name) == 0) {
            *sym_index = n;
            return s->st_value;
        }

    } while ((params->gun_hash_table.chain[n++] & 1) == 0);

    find_undefine:
    for (n = 0, s = params->p_dt_sym; n < params->gun_hash_table.symndx; n++, s++) {
        if (strcmp(params->p_dt_strtab + s->st_name, name) == 0) {
            *sym_index = n;
            return s->st_value;
        }
    }
    *sym_index = -1;
    return 0;
}

static gaddress file_offset_to_vaddress(program_table_area *areas, int num, gsize offset) {
    gaddress retval = 0;
    for (int i = 0; i < num; ++i) {
        if (offset >= areas[i].file_start_offset &&
            offset - areas[i].file_start_offset < areas[i].file_length) {
            retval = offset - areas[i].file_start_offset + areas[i].vaddr_start;
            break;
        }
    }
    return retval;
}

static uint32_t calculate_elf_hash(const char *name) {
    const uint8_t *name_bytes = (const uint8_t *) name;
    uint32_t h = 0, g;

    while (*name_bytes) {
        h = (h << 4) + *name_bytes++;
        g = h & 0xf0000000;
        h ^= g;
        h ^= g >> 24;
    }
    return h;
}

static uint32_t calculate_elf_gnu_hash(const char *name) {
    const uint8_t *name_bytes = (const uint8_t *) name;
    uint32_t h = 5381;

    while (*name_bytes != 0) {
        h += (h << 5) + *name_bytes++;
    }
    return h;
}